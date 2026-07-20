package com.persons.finder.web

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.cfg.CoercionAction
import tools.jackson.databind.cfg.CoercionInputShape
import tools.jackson.databind.type.LogicalType

@Configuration(proxyBeanMethods = false)
class StrictJsonConfiguration {
    @Bean
    fun strictScalarJsonMapper(): JsonMapperBuilderCustomizer =
        JsonMapperBuilderCustomizer { builder ->
            builder.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            builder.withCoercionConfig(LogicalType.Textual) { coercion ->
                coercion.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                coercion.setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                coercion.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
            }
            builder.withCoercionConfig(LogicalType.Float) { coercion ->
                coercion.setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                coercion.setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail)
                coercion.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
            }
        }
}
