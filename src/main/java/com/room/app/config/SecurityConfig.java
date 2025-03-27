package com.room.app.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.room.app.dto.User;
import com.room.app.repository.UserRepository;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final JwtUtil jwtUtil;
	private static final List<String> ALLOWED_ORIGINS = Arrays.asList("http://localhost:3000","http://localhost:3001","http://192.168.29.165:3001",
			"https://room-react-git-master-anands-projects-607fcd69.vercel.app",
			"https://room-react-h0aj1lmb1-anands-projects-607fcd69.vercel.app", 
			"https://roomtracker.fun","https://react-fornend.vercel.app","https://react-fornend.vercel.app");

	public SecurityConfig(JwtUtil jwtUtil) {
		this.jwtUtil = jwtUtil;
	}

	@Bean
	public UserDetailsService userDetailsService(UserRepository userRepository) {
		return username -> {
			Optional<User> userOptional = userRepository.findByUsername(username);
			if (userOptional.isEmpty()) {
				throw new UsernameNotFoundException("User not found with username: " + username);
			}

			User user = userOptional.get();
			return new org.springframework.security.core.userdetails.User(user.getUsername(), user.getPassword(),
					user.isEnabled(), true, // accountNonExpired
					true, // credentialsNonExpired
					true, // accountNonLocked
					Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
		};
	}

	@Bean
	public AuthenticationProvider authenticationProvider(UserRepository userRepository) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
		provider.setUserDetailsService(userDetailsService(userRepository));
		provider.setPasswordEncoder(passwordEncoder());
		provider.setHideUserNotFoundExceptions(false); // Important for proper error messages
		return provider;
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
		return config.getAuthenticationManager();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(12); // Stronger hashing
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, UserRepository userRepository,
			UserDetailsService userDetailsService) throws Exception {

		http.csrf(AbstractHttpConfigurer::disable).cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers("/auth/login", "/auth/register", "/auth/logout").permitAll()
						.requestMatchers("/api/expenses/**", "/api/exports/**", "/api/members/**","/auth/users/**").permitAll()
						.requestMatchers(HttpMethod.PUT, "/auth/update/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.DELETE, "/api/expenses/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.PUT, "/api/expenses/clear/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.PUT, "/api/expenses/**").hasRole("ADMIN").anyRequest()
						.authenticated())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.addFilterBefore(jwtAuthenticationFilter(userDetailsService),
						UsernamePasswordAuthenticationFilter.class)
				.authenticationProvider(authenticationProvider(userRepository));

		return http.build();
	}

	private JwtAuthenticationFilter jwtAuthenticationFilter(UserDetailsService userDetailsService) {
		return new JwtAuthenticationFilter(jwtUtil, userDetailsService);
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(ALLOWED_ORIGINS);
		configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
		configuration
				.setAllowedHeaders(Arrays.asList("Authorization", "Cache-Control", "Content-Type", "X-Requested-With"));
		configuration.setExposedHeaders(Arrays.asList("Authorization"));
		configuration.setAllowCredentials(true);
		configuration.setMaxAge(3600L); // 1 hour

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}