package org.jboss.arquillian.graphene.proxy;

import org.jboss.arquillian.graphene.cglib.MethodInterceptor;

class ClassImposterizer extends org.jboss.arquillian.graphene.cglib.ClassImposterizer {
    static <T> T imposterise(MethodInterceptor interceptor, Class<T> mockedType, Class<?>... ancillaryTypes) {
        return ClassImposterizer.getInstance().imposteriseProtected(interceptor, mockedType, ancillaryTypes);
    }
}
