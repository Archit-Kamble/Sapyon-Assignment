package com.agencydesk.auth;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.agencydesk.common.ApiException;

/** Lets controllers declare an {@code Actor actor} parameter; fails with 403 if no agency is selected. */
@Component
public class ActorArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == Actor.class;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav,
                                  NativeWebRequest request, WebDataBinderFactory binderFactory) {
        Object actor = request.getAttribute(AuthInterceptor.ACTOR, RequestAttributes.SCOPE_REQUEST);
        if (actor == null) {
            throw ApiException.forbidden("select a membership first");
        }
        return actor;
    }
}
