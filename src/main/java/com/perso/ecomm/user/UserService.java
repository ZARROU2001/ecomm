package com.perso.ecomm.user;

import com.perso.ecomm.CustomUser.CustomUserDetails;
import com.perso.ecomm.JWT.JWTUtil;
import com.perso.ecomm.exception.DuplicateResourceException;
import com.perso.ecomm.exception.ResourceNotFoundException;
import com.perso.ecomm.playLoad.request.LoginRequest;
import com.perso.ecomm.playLoad.request.SignupRequest;
import com.perso.ecomm.playLoad.request.UserUpdateRequest;
import com.perso.ecomm.playLoad.request.changePasswordRequest;
import com.perso.ecomm.playLoad.response.UserInfoResponse;
import com.perso.ecomm.role.ERole;
import com.perso.ecomm.role.Role;
import com.perso.ecomm.role.RoleRepository;
import com.perso.ecomm.util.FileUploadUtil;
import io.micrometer.common.util.internal.logging.InternalLogger;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;


@Service
@Slf4j
public class UserService {


    private final AuthenticationManager authenticationManager;
    private final JWTUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    private final RoleRepository roleRepository;

    final String FOLDER_PATH = "src/main/resources/static/images";

    @Value("${upload.user-path}")
    private String userImagePath;


    public UserService(AuthenticationManager authenticationManager, JWTUtil jwtUtil, PasswordEncoder passwordEncoder, UserRepository userRepository, RoleRepository roleRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }


    public List<User> getUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("There's no user with id:" + id));
    }


    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(
                () -> new ResourceNotFoundException("There's no user with id:" + userId)
        );
        userRepository.delete(user);
    }

    @Transactional
    public User updateUser(Long userId, UserUpdateRequest request) throws IOException {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User with id " + userId + " doesn't exist"));

        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());

        MultipartFile image = request.getImageUrl();

        if (image != null && !image.isEmpty()) {

            if (!image.getContentType().startsWith("image/")) {
                throw new IllegalArgumentException("Only image files are allowed");
            }

            // 1️⃣ Delete old image if not default
            deleteOldUserImageIfNeeded(user.getImageUrl());

            // 2️⃣ Generate safe filename
            String extension = StringUtils.getFilenameExtension(image.getOriginalFilename());
            String fileName = UUID.randomUUID() + "." + extension;

            // 3️⃣ Save new image
            FileUploadUtil.saveFile(userImagePath, fileName, image);

            // 4️⃣ Update image URL
            user.setImageUrl("/images/users/" + fileName);
        }

        return user;
    }


    private void deleteOldUserImageIfNeeded(String imageUrl) {

        if (imageUrl == null || imageUrl.contains("default-image.png")) {
            return; // never delete default image
        }

        try {
            String fileName = Paths.get(imageUrl).getFileName().toString();
            Path filePath = Paths.get(userImagePath).resolve(fileName);
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            // log warning, do NOT fail transaction
            log.warn("Could not delete old user image: {}", imageUrl);
        }
    }


    @Transactional
    public void changePassword(Long userId, changePasswordRequest passwordRequest) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        " User with id " + userId + " doesn't exist "));
        user.setPassword(passwordRequest.getNewPassword());
    }

    public UserInfoResponse login(LoginRequest loginRequest) {

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );

        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        User user = userRepository.findByEmailOrUsername(principal.getUsername(), principal.getUsername());
        String token = jwtUtil.issueToken(principal.getUsername(), List.of(principal.getAuthorities().toString()));


        return new UserInfoResponse(token, user);
    }

    public User registerNewUser(SignupRequest signupRequest) throws IOException {

        if (userRepository.existsByUsername(signupRequest.getUsername())) {
            throw new DuplicateResourceException("Username already taken");
        }

        if (userRepository.existsByEmail(signupRequest.getEmail())) {
            throw new DuplicateResourceException("Email already taken");
        }

        String imageUrl = "/images/users/default-image.png";

        MultipartFile image = signupRequest.getImageUrl();

        if (image != null && !image.isEmpty()) {

            if (!image.getContentType().startsWith("image/")) {
                throw new IllegalArgumentException("Only image files are allowed");
            }

            String extension = StringUtils.getFilenameExtension(image.getOriginalFilename());
            String fileName = UUID.randomUUID() + "." + extension;

            FileUploadUtil.saveFile(userImagePath, fileName, image);

            imageUrl = "/images/users/" + fileName;
        }

        User user = new User(
                signupRequest.getEmail(),
                passwordEncoder.encode(signupRequest.getPassword()),
                signupRequest.getFirstName(),
                signupRequest.getLastName(),
                signupRequest.getUsername(),
                imageUrl
        );

        Role role = resolveRole(signupRequest.getRole());
        user.setRole(role);

        return userRepository.save(user);
    }

    private Role resolveRole(String roleName) {
        if ("admin".equalsIgnoreCase(roleName)) {
            return roleRepository.findRoleByName(ERole.ROLE_ADMIN)
                    .orElseThrow(() -> new ResourceNotFoundException("Role admin not found"));
        }
        if ("moderator".equalsIgnoreCase(roleName)) {
            return roleRepository.findRoleByName(ERole.ROLE_MODERATOR)
                    .orElseThrow(() -> new ResourceNotFoundException("Role moderator not found"));
        }
        return roleRepository.findRoleByName(ERole.ROLE_USER)
                .orElseThrow(() -> new ResourceNotFoundException("Role user not found"));
    }

    public String logoutUser() {
        return "logged out";
    }

    public Page<User> getSortedAndPagedData(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    @Transactional
    public void changeRole(Long userId, String role) {
        User user = userRepository.findById(userId).orElseThrow(
                () -> new ResourceNotFoundException("user with id : " + userId + " not found")
        );

        Role targetRole = switch (role.toLowerCase()) {
            case "admin" -> roleRepository.findRoleByName(ERole.ROLE_ADMIN)
                    .orElseThrow(() -> new ResourceNotFoundException("admin role not found"));
            case "moderator" -> roleRepository.findRoleByName(ERole.ROLE_MODERATOR)
                    .orElseThrow(() -> new ResourceNotFoundException("moderator role not found"));
            case "user" -> roleRepository.findRoleByName(ERole.ROLE_USER)
                    .orElseThrow(() -> new ResourceNotFoundException("user role not found"));
            default -> throw new ResourceNotFoundException("Invalid role: " + role);
        };

        user.setRole(targetRole);
    }

    public void changePhoto(Long userId, MultipartFile multipartFile) throws IOException {

        User user = userRepository.findById(userId).orElseThrow(
        );

        String a = "";
        if (multipartFile != null) {
            FileUploadUtil.saveFile(FOLDER_PATH, multipartFile.getOriginalFilename(), multipartFile);
            a = multipartFile.getOriginalFilename();
        }

        user.setImageUrl("http://localhost:8080/images/" + a);

    }
}
