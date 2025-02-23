package io.moderne.jsonrpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.ConstructorDetector;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

import java.lang.reflect.ParameterizedType;
import java.util.Map;

public interface NamedParamJsonRpcMethod<P> extends JsonRpcMethod {
    @Override
    default Object handle(Object params) throws Exception {
        //noinspection unchecked
        Class<P> paramType = (Class<P>) ((ParameterizedType) getClass().getGenericSuperclass())
                .getActualTypeArguments()[0];
        return accept(NamedParamConverter.convert(params, paramType));
    }

    Object accept(P params) throws Exception;
}

class NamedParamConverter {
    private static final ObjectMapper mapper = JsonMapper.builder()
            // to be able to construct classes that have @Data and a single field
            // see https://cowtowncoder.medium.com/jackson-2-12-most-wanted-3-5-246624e2d3d0
            .constructorDetector(ConstructorDetector.USE_PROPERTIES_BASED)
            .build()
            .registerModules(new ParameterNamesModule(), new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    static <T> T convert(Object params, Class<T> clazz) throws Exception {
        if (params instanceof Map) {
            //noinspection unchecked
            Map<String, Object> paramMap = (Map<String, Object>) params;
            return mapper.convertValue(paramMap, clazz);
        } else {
            throw new Exception("Expected a named parameter map for method");
        }
    }
}
