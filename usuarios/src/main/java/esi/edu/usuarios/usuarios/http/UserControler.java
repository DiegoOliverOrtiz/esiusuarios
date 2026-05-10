package esi.edu.usuarios.usuarios.http;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import esi.edu.usuarios.usuarios.dto.LoginRequest;
import esi.edu.usuarios.usuarios.dto.LoginResponse;
import esi.edu.usuarios.usuarios.dto.RegisterUserRequest;
import esi.edu.usuarios.usuarios.dto.TwoFactorSetupResponse;
import esi.edu.usuarios.usuarios.dto.TwoFactorVerifyRequest;
import esi.edu.usuarios.usuarios.dto.UpdateProfileRequest;
import esi.edu.usuarios.usuarios.dto.UserResponse;
import esi.edu.usuarios.usuarios.model.User;
import esi.edu.usuarios.usuarios.services.RateLimiterService;
import esi.edu.usuarios.usuarios.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/users")
@Validated
public class UserControler {
    private static final String AUTH_GENERIC_ERROR = "No se ha podido completar la solicitud. Revisa los datos e intentalo de nuevo.";
    private static final String SESSION_COOKIE = "session_id";
    private final Logger logger = LoggerFactory.getLogger(UserControler.class);

    @Autowired
    private UserService service;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Value("${app.session.cookie.secure:true}")
    private boolean secureCookie;

    @Value("${app.session.cookie.same-site:Lax}")
    private String sameSite;

    @Value("${app.session.cookie.max-age-seconds:7200}")
    private long sessionMaxAgeSeconds;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest credenciales, HttpServletRequest request) {
        String ip = clientIp(request);
        rateLimiterService.check("login", ip + "|" + credenciales.getName(), 5, Duration.ofMinutes(15));

        User user = service.authenticate(credenciales.getName(), credenciales.getPwd())
            .orElseThrow(() -> {
                logger.warn("Login fallido ip={} login={}", ip, safeLogin(credenciales.getName()));
                return new ResponseStatusException(HttpStatus.UNAUTHORIZED, AUTH_GENERIC_ERROR);
            });

        if (user.isTwoFactorEnabled()) {
            String challengeToken = service.beginTwoFactorLogin(user);
            logger.info("Login pendiente de 2FA usuarioId={} ip={}", user.getId(), ip);
            return ResponseEntity.ok(new LoginResponse(challengeToken, user));
        }

        user = service.startSession(user);
        logger.info("Login correcto usuarioId={} ip={}", user.getId(), ip);

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, sessionCookie(user.getToken()).toString())
            .body(new LoginResponse(user));
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        try {
            User user = service.startSession(service.register(request));
            return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, sessionCookie(user.getToken()).toString())
                .body(new UserResponse(user));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AUTH_GENERIC_ERROR);
        }
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@CookieValue(name = SESSION_COOKIE, required = false) String token) {
        return service.profileBySessionToken(token)
            .map(ResponseEntity::ok)
            .orElseGet(() -> {
                logger.warn("Acceso denegado a /users/me por sesion ausente o invalida");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            });
    }

    @PutMapping("/me")
    public UserResponse updateMe(
        @CookieValue(name = SESSION_COOKIE, required = false) String token,
        @Valid @RequestBody UpdateProfileRequest request
    ) {
        try {
            return service.updateProfile(token, request);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AUTH_GENERIC_ERROR);
        }
    }

    @PostMapping("/2fa/setup")
    public TwoFactorSetupResponse setupTwoFactor(@CookieValue(name = SESSION_COOKIE, required = false) String token) {
        try {
            return service.setupTwoFactor(token);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, AUTH_GENERIC_ERROR);
        }
    }

    @PostMapping("/2fa/verify")
    public UserResponse verifyTwoFactorSetup(
        @CookieValue(name = SESSION_COOKIE, required = false) String token,
        @Valid @RequestBody TwoFactorVerifyRequest request
    ) {
        try {
            return service.verifyAndEnableTwoFactor(token, request.getCode());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, AUTH_GENERIC_ERROR);
        }
    }

    @PostMapping("/2fa/login/verify")
    public ResponseEntity<UserResponse> verifyTwoFactorLogin(@Valid @RequestBody TwoFactorVerifyRequest request) {
        try {
            User user = service.completeTwoFactorLogin(request.getChallengeToken(), request.getCode());
            return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookie(user.getToken()).toString())
                .body(new UserResponse(user));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, AUTH_GENERIC_ERROR);
        }
    }

    @PostMapping("/2fa/disable")
    public UserResponse disableTwoFactor(@CookieValue(name = SESSION_COOKIE, required = false) String token) {
        try {
            return service.disableTwoFactor(token);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, AUTH_GENERIC_ERROR);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = SESSION_COOKIE, required = false) String token) {
        service.logout(token);
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, clearSessionCookie().toString())
            .build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> cancelAccount(@CookieValue(name = SESSION_COOKIE, required = false) String token) {
        try {
            service.cancelAccount(token);
            return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearSessionCookie().toString())
                .build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> rejectAccountDeletionById(@PathVariable String id) {
        logger.warn("Intento de cancelar cuenta por id bloqueado: {}", id);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Void> rejectAccountReadById(@PathVariable String id) {
        logger.warn("Intento de leer cuenta por id bloqueado: {}", id);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> rejectAccountUpdateById(@PathVariable String id) {
        logger.warn("Intento de modificar cuenta por id bloqueado: {}", id);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @GetMapping("/confirm")
    public String confirm(@RequestParam String token) {
        try {
            return service.confirmRegistration(token);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleValidationError() {
        return AUTH_GENERIC_ERROR;
    }

    private ResponseCookie sessionCookie(String token) {
        return ResponseCookie.from(SESSION_COOKIE, token)
            .httpOnly(true)
            .secure(secureCookie)
            .sameSite(sameSite)
            .path("/")
            .maxAge(Duration.ofSeconds(sessionMaxAgeSeconds))
            .build();
    }

    private ResponseCookie clearSessionCookie() {
        return ResponseCookie.from(SESSION_COOKIE, "")
            .httpOnly(true)
            .secure(secureCookie)
            .sameSite(sameSite)
            .path("/")
            .maxAge(Duration.ZERO)
            .build();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String safeLogin(String login) {
        if (login == null || login.isBlank()) {
            return "<vacio>";
        }
        String trimmed = login.strip().toLowerCase();
        int at = trimmed.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return trimmed.charAt(0) + "***" + trimmed.substring(at);
    }
}
