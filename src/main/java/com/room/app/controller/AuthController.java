package com.room.app.controller;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.room.app.config.JwtUtil;
import com.room.app.dto.User;
import com.room.app.dto.UserResponse;
import com.room.app.repository.UserRepository;
import com.room.app.service.ResourceNotFoundException;
import com.room.app.service.UserService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.core.HttpHeaders;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "http://localhost:3000")
public class AuthController {

	private final UserDetailsService userDetailsService;
	private final PasswordEncoder passwordEncoder;
	private final JwtUtil jwtUtil;
	private final UserService userService;
	private final UserRepository userRepository;

	private final AuthenticationManager authenticationManager;

	@Autowired
	public AuthController(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
			UserService userService, // Add this
			UserRepository userRepository, AuthenticationManager authenticationManager // Add this
	) {
		this.userDetailsService = userDetailsService;
		this.passwordEncoder = passwordEncoder;
		this.jwtUtil = jwtUtil;
		this.userService = userService;
		this.userRepository = userRepository;
		this.authenticationManager = authenticationManager;
	}

	@PostMapping("/login")
	public ResponseEntity<?> login(@RequestBody User user) {
		try {
			// Authenticate using Spring Security's authentication manager
			Authentication authentication = authenticationManager
					.authenticate(new UsernamePasswordAuthenticationToken(user.getUsername(), user.getPassword()));

			// Only proceed if authentication succeeds
			UserDetails userDetails = (UserDetails) authentication.getPrincipal();

			String role = userDetails.getAuthorities().stream().findFirst().map(GrantedAuthority::getAuthority)
					.orElse("ROLE_USER").replace("ROLE_", "");

			String token = jwtUtil.generateToken(userDetails);

			return ResponseEntity.ok().header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
					.body(Map.of("token", token, "username", userDetails.getUsername(), "role", role));
		} catch (BadCredentialsException e) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid username or password");
		} catch (AuthenticationException e) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication failed");
		}
	}

	@PostMapping("/register")
	public ResponseEntity<Map<String, String>> register(@RequestBody User user) {
		Map<String, String> response = new HashMap<>();
		try {
			userService.register(user);
			response.put("status", "success");
			response.put("message", "User registered successfully");
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			response.put("status", "failed");
			response.put("message", e.getMessage());
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
		}
	}

	@PostMapping("/logout")
	public ResponseEntity<Map<String, String>> logout(HttpServletRequest request, HttpServletResponse response) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}

		Cookie cookie = new Cookie("JSESSIONID", null);
		cookie.setPath("/");
		cookie.setMaxAge(0);
		cookie.setHttpOnly(true);
		response.addCookie(cookie);

		response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
		response.setHeader("Pragma", "no-cache");
		response.setHeader("Expires", "0");

		return ResponseEntity.ok().body(Map.of("status", "success", "message", "Logged out successfully"));
	}

	@GetMapping("/me")
	public ResponseEntity<Map<String, Object>> getUserDetails() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(Collections.singletonMap("error", "Unauthorized"));
		}

		String username = authentication.getName();
		Optional<User> user = userRepository.findByUsername(username);

		if (user.isEmpty()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(Collections.singletonMap("error", "User not found"));
		}

		Map<String, Object> response = new HashMap<>();
		response.put("username", user.get().getUsername());
		response.put("role", user.get().getRole());

		return ResponseEntity.ok(response);
	}

	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	@PutMapping("/update/{userId}/role")
	public ResponseEntity<UserResponse> updateUserRole(@PathVariable Long userId, @RequestParam String newRole)
			throws ResourceNotFoundException {
		return ResponseEntity.ok(userService.updateUserRole(userId, newRole));
	}

	@GetMapping("/users")
	public ResponseEntity<List<UserResponse>> getAllUsers() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		return ResponseEntity.ok(userService.getAllUsers());
	}

}
