package ru.promo.finance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.promo.finance.domain.model.User;
import ru.promo.finance.domain.model.Wallet;
import ru.promo.finance.domain.repository.UserRepository;
import ru.promo.finance.domain.repository.WalletRepository;
import ru.promo.finance.infrastructure.storage.FileStorageService;
import ru.promo.finance.infrastructure.storage.InMemoryUserRepository;
import ru.promo.finance.infrastructure.storage.InMemoryWalletRepository;
import ru.promo.finance.service.AuthService;
import ru.promo.finance.service.CategoryService;
import ru.promo.finance.service.SecurityService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class UserAndAuthTest {
    private UserRepository userRepository;
    private WalletRepository walletRepository;
    private SecurityService securityService;
    private FileStorageService fileStorageService;
    private CategoryService categoryService;
    private AuthService authService;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("finance-test-auth");
        userRepository = new InMemoryUserRepository();
        walletRepository = new InMemoryWalletRepository();
        securityService = new SecurityService();
        fileStorageService = new FileStorageService(tempDir);
        categoryService = new CategoryService();
        authService = new AuthService(userRepository, walletRepository, securityService, fileStorageService, categoryService);
    }

    @Test
    void testRegisterSuccess() {
        User user = authService.register("alex", "pass123");
        assertNotNull(user);
        assertEquals("alex", user.getLogin());
        assertTrue(userRepository.existsByLogin("alex"));
        assertTrue(walletRepository.findByUserId(user.getId()).isPresent());
    }

    @Test
    void testRegisterDuplicateLoginThrowsException() {
        authService.register("alex", "pass123");
        assertThrows(IllegalArgumentException.class, () -> authService.register("alex", "otherPass"));
    }

    @Test
    void testRegisterInvalidLoginThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> authService.register("", "pass123"));
        assertThrows(IllegalArgumentException.class, () -> authService.register("al", "pass123"));
        assertThrows(IllegalArgumentException.class, () -> authService.register("alex@user", "pass123"));
    }

    @Test
    void testRegisterShortPasswordThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> authService.register("alex", "123"));
    }

    @Test
    void testLoginSuccess() {
        authService.register("maria", "secret123");
        User loggedIn = authService.login("maria", "secret123");
        assertNotNull(loggedIn);
        assertEquals("maria", loggedIn.getLogin());
    }

    @Test
    void testLoginWrongPasswordThrowsException() {
        authService.register("maria", "secret123");
        assertThrows(IllegalArgumentException.class, () -> authService.login("maria", "wrongPass"));
    }

    @Test
    void testLoginNonExistentUserThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> authService.login("ghost", "pass"));
    }

    @Test
    void testPasswordHashingAndSaltUniqueness() {
        String salt1 = securityService.generateSalt();
        String salt2 = securityService.generateSalt();
        assertNotEquals(salt1, salt2);

        String hash1 = securityService.hashPassword("testPass", salt1);
        String hash2 = securityService.hashPassword("testPass", salt2);
        assertNotEquals(hash1, hash2);

        assertTrue(securityService.verifyPassword("testPass", salt1, hash1));
        assertFalse(securityService.verifyPassword("wrongPass", salt1, hash1));
    }
}
