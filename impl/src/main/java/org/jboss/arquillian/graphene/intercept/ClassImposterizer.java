package org.jboss.arquillian.graphene.intercept;

import org.jboss.arquillian.graphene.cglib.MethodInterceptor;

class ClassImposterizer extends org.jboss.arquillian.graphene.cglib.ClassImposterizer {
    static  <T> T imposterise(MethodInterceptor interceptor, Class<T> mockedType, Class<?>... ancillaryTypes) {
        return getInstance().imposteriseProtected(interceptor, mockedType, ancillaryTypes);
    }
}
