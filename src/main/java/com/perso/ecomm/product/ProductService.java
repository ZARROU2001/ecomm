package com.perso.ecomm.product;

import com.perso.ecomm.exception.ResourceNotFoundException;
import com.perso.ecomm.playLoad.request.ProductRequest;
import com.perso.ecomm.productCategory.ProductCategory;
import com.perso.ecomm.productCategory.ProductCategoryRepository;
import com.perso.ecomm.util.FileUploadUtil;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

@Service
public class ProductService {
    @Value("${upload.path}")
    private String uploadPath;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository productCategoryRepository;

    public ProductService(ProductRepository productRepository, ProductCategoryRepository productCategoryRepository) {
        this.productRepository = productRepository;
        this.productCategoryRepository = productCategoryRepository;
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public List<Product> getProductsByCategory(Long categoryId) {
        ProductCategory category = productCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        return productRepository.findByCategory(category);
    }

    public Page<Product> getSortedAndPagedProductsByCategory(Long categoryId, Pageable pageable) {
        ProductCategory productCategory = productCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        return productRepository.findAllByCategory(productCategory, pageable);
    }

    public Page<Product> getSortedAndPagedData(Pageable pageable) {
        return productRepository.findAll(pageable);
    }


    // get latest products
    public List<Product> getLatestProducts() {
        Pageable pageable = PageRequest.of(0, 10);  // Fetching the latest 10 products
        return productRepository.findLatestByCreationDateDesc(pageable);
    }

    //get hot deals
    public List<Product> getHotDealsProducts() {
        return productRepository.findByDiscountPercentGreaterThan(45);
    }

    public void deleteProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product with ID + categoryId + not found"));
        productRepository.delete(product);

    }

    public Product registerNewProduct(ProductRequest productRequest) throws IOException {

        ProductCategory productCategory = productCategoryRepository
                .findProductCategoriesByCategoryName(productRequest.getCategory())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        MultipartFile image = productRequest.getImageUrl();


        if (image.isEmpty() || !image.getContentType().startsWith("image/")) {
            throw new IllegalArgumentException("Invalid image file");
        }

        String extension = StringUtils.getFilenameExtension(image.getOriginalFilename());
        String fileName = UUID.randomUUID() + "." + extension;

        FileUploadUtil.saveFile(uploadPath, fileName, image);

        double discountPercent = calculateDiscountPercent(
                productRequest.getPriceBeforeDiscount(),
                productRequest.getPriceAfterDiscount()
        );

        Product product = new Product(
                productCategory,
                productRequest.getName(),
                productRequest.getDescription(),
                productRequest.getPriceAfterDiscount(),
                productRequest.getPriceBeforeDiscount(),
                productRequest.getStockQuantity(),
                "/images/" + fileName
        );

        product.setDiscountPercent(discountPercent);

        return productRepository.save(product);
    }


    @Transactional
    public Product updateProduct(Long productId, ProductRequest productRequest) throws IOException {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException( " product with id " + productId + " doesn't exist "));

        ProductCategory productCategory = productCategoryRepository.findProductCategoriesByCategoryName(productRequest.getCategory())
                .orElseThrow(() -> new ResourceNotFoundException("Category not Found"));

        product.setName(productRequest.getName());
        product.setDescription(productRequest.getDescription());
        product.setPriceAfterDiscount(productRequest.getPriceAfterDiscount());
        product.setPriceBeforeDiscount(productRequest.getPriceBeforeDiscount());
        product.setStockQuantity(productRequest.getStockQuantity());
        product.setCategory(productCategory);
        if (productRequest.getImageUrl()!=null){
            FileUploadUtil.saveFile(uploadPath, productRequest.getImageUrl().getOriginalFilename(), productRequest.getImageUrl());
            product.setImageUrl("http://localhost:8080/images/" + productRequest.getImageUrl().getOriginalFilename());
        }
        return product;
    }

    public byte[] getImage(Long id) throws IOException {
        Product product = productRepository.findById(id).get();
        String filePath = uploadPath + product.getImageUrl();

        return Files.readAllBytes(new File(filePath).toPath());
    }


    public Product getProductById(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product with id " + productId + " not found"));
    }

    public double calculateDiscountPercent(double priceBeforeDiscount, double priceAfterDiscount) {
        if (priceBeforeDiscount > 0 && priceBeforeDiscount <= priceAfterDiscount) {
            throw new IllegalArgumentException("Price before discount must be greater than Price After discount");
        }
        double discount = ((priceBeforeDiscount - priceAfterDiscount) / priceBeforeDiscount) * 100;
        return (int) Math.round(discount);
    }

}
