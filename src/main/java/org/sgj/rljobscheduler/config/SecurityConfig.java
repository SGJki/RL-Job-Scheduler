package org.sgj.rljobscheduler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
//        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
//                .and()
//                .authorizeRequests()
//                //放行登录和hello
//                .antMatchers("/api/auth/**").permitAll()
//                .antMatchers("/hello/**").permitAll()
//                .anyRequest().authenticated();
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
//        http.authorizeHttpRequests(HttpSecurity::antMatchers("/api/auth/**","/login"))
                // 放行登录和hello
//                .antMatchers("/api/auth/**").permitAll()
                // 授权规则
//                .authorizeRequests(auth -> auth
//                        // 放行登录和注册接口
//                        .antMatchers("/api/auth/**").permitAll()
//                        // 放行静态资源 (根据实际情况调整)
//                        .antMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
//                        // 其他所有请求都需要认证
//                        .anyRequest().authenticated()
//                )
                // 将 JWT 过滤器添加到 UsernamePasswordAuthenticationFilter 之前
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);



        return http.build();
    }
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    }

