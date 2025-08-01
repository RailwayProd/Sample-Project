package uz.shukrullaev.com.sample

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableAsync

@EnableJpaAuditing
@SpringBootApplication
@ComponentScan("uz.shukrullaev.com")
@EnableJpaRepositories(
    basePackages = ["uz.shukrullaev.com.sample"],
    repositoryBaseClass = BaseRepositoryImpl::class,
)
@EnableAsync
class ZeroOneSampleApplication

fun main(args: Array<String>) {
    runApplication<ZeroOneSampleApplication>(*args)
}
