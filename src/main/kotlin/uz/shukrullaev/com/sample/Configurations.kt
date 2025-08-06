package uz.shukrullaev.com.sample

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component
import org.springframework.web.servlet.LocaleResolver
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver
import java.security.Key
import java.util.*


/**
 * @see uz.shukrullaev.com.sample
 * @author Abdulloh
 * @since 11/07/2025 4:48 pm
 */

@Configuration
class WebMvcConfigure : WebMvcConfigurer {

    @Bean
    @Primary
    fun messageSource(): ResourceBundleMessageSource {
        return ResourceBundleMessageSource().apply {
            setDefaultEncoding("UTF-8")
            setDefaultLocale(Locale("uz"))
            setBasename("errors")
        }
    }

    @Bean
    fun localeResolver(): LocaleResolver = CustomLocaleResolver()

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOrigins(
                "http://192.168.0.1:3000",
                "http://192.168.0.1:3001",
                "http://192.168.0.1:3002",
                "http://192.168.0.1:3003",
                "http://192.168.0.1:3004",
                "http://192.168.0.1:3005",
                "https://9341394f7f65.ngrok-free.app",
                "http://localhost:3004",
                "http://172.20.16.1:3004",
                "https://contracts-demo.netlify.app"
            )
            .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600)
    }
}

class CustomLocaleResolver : AcceptHeaderLocaleResolver() {
    override fun resolveLocale(request: HttpServletRequest): Locale {
        return request.getHeader("Hl")
            ?.let { Locale(it) } ?: Locale("uz")
    }
}

@EnableMethodSecurity(prePostEnabled = true)
@Configuration
class SecurityConfig(private val messageSource: MessageSource) {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(4)


    private val secret = "superSecretKeyForJwtSigningChangeThisToStrongKey123!"

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { }
            .csrf { it.disable() }
            .authorizeHttpRequests {
                it.requestMatchers(
                    "/api/users/auth/**",
                    "/api/download-info/stream"
                ).permitAll()
                it.anyRequest().authenticated()
            }
            .exceptionHandling {
                it.authenticationEntryPoint(CustomAuthenticationEntryPoint(messageSource))
                it.accessDeniedHandler(CustomAccessDeniedHandler(messageSource))
            }
            .oauth2ResourceServer { configurer ->
                configurer.jwt { jwt ->
                    jwt.jwtAuthenticationConverter(CustomJwtAuthenticationConverter())
                }
            }

        return http.build()
    }

    @Bean
    fun jwtDecoder(): JwtDecoder {
        val key = Keys.hmacShaKeyFor(secret.toByteArray())
        return NimbusJwtDecoder.withSecretKey(key).build()
    }
}

class CustomJwtAuthenticationConverter : Converter<Jwt, AbstractAuthenticationToken> {
    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        val id = jwt.getClaimAsString("id")?.toLongOrNull()
            ?: throw BadCredentialsException("Missing ID in JWT")

        val username = jwt.subject

        val roles: List<String> = when (val rawRoles = jwt.claims["roles"]) {
            is String -> listOf(rawRoles)
            is Collection<*> -> rawRoles.filterIsInstance<String>()
            else -> emptyList()
        }
        val authorities = roles.map { SimpleGrantedAuthority("ROLE_$it") }

        val principal = CustomUserDetails(id, username, authorities)
        return UsernamePasswordAuthenticationToken(principal, jwt.tokenValue, authorities)
    }
}


class CustomAccessDeniedHandler(
    private val messageSource: MessageSource,
) : AccessDeniedHandler {

    override fun handle(
        request: HttpServletRequest?,
        response: HttpServletResponse?,
        accessDeniedException: org.springframework.security.access.AccessDeniedException?,
    ) {
        val locale = request?.locale
        val message = locale?.let { messageSource.getMessage("access.denied", null, it) }

        response?.status = HttpServletResponse.SC_FORBIDDEN
        response?.contentType = "application/json"
        response?.writer?.write("""{ "message": "$message" }""")
    }
}


class CustomAuthenticationEntryPoint(
    private val messageSource: MessageSource,
) : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        val locale = Locale("uz")
        val message = messageSource.getMessage("auth.unauthorized", null, locale)

        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = "application/json"
        response.writer.write("""{ "message": "$message" }""")
    }
}


@Component
class JwtUtil {

    private val secret = "superSecretKeyForJwtSigningChangeThisToStrongKey123!"
    private val expirationMillis = 60 * 60 * 24 * 1000L
    private val key: Key = Keys.hmacShaKeyFor(secret.toByteArray())


    fun generateToken(user: User): TokenDTO {
        val now = Date()
        val expiry = Date(now.time + expirationMillis)
        val claims = Jwts.claims().apply {
            subject = user.username
            this["id"] = user.id.toString()
            this["roles"] = listOf(user.role.name)
        }

        val token = Jwts.builder()
            .setClaims(claims)
            .setIssuedAt(now)
            .setExpiration(expiry)
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()

        return TokenDTO(token, now, expiry)
    }
}