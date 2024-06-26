package org.jboss.arquillian.graphene.proxy;

import org.jboss.arquillian.graphene.bytebuddy.MethodInterceptor;

class ClassImposterizer extends org.jboss.arquillian.graphene.bytebuddy.ClassImposterizer {

    static final ClassImposterizer INSTANCE = new ClassImposterizer();

    <T> T imposterise(MethodInterceptor interceptor, Class<T> mockedType, Class<?>... ancillaryTypes) {
        return INSTANCE.imposteriseProtected(interceptor, mockedType, ancillaryTypes);
    }
}
