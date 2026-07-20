package com.persons.finder.shared.time

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.Duration

@Configuration
class TimeConfiguration {
    @Bean
    fun clock(): Clock =
        Clock.tick(
            Clock.systemUTC(),
            SERVER_TIMESTAMP_RESOLUTION,
        )

    private companion object {
        val SERVER_TIMESTAMP_RESOLUTION: Duration = Duration.ofNanos(1_000)
    }
}
