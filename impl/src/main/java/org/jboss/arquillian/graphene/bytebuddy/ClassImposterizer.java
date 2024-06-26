/**
 * Thanks to Mockito guys for some modifications. This class has been further modified for use in lambdaj
 * and then modified for use in Arquillian Graphene project.
 *
 * Mockito License for redistributed, modified file.
 *
Copyright (c) 2007 Mockito contributors
This program is made available under the terms of the MIT License.
 *
 *
 * jMock License for original distributed file
 *
Copyright (c) 2000-2007, jMock.org
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list of
conditions and the following disclaimer. Redistributions in binary form must reproduce
the above copyright notice, this list of conditions and the following disclaimer in
the documentation and/or other materials provided with the distribution.

Neither the name of jMock nor the names of its contributors may be used to endorse
or promote products derived from this software without specific prior written
permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
DAMAGE.
*/
package org.jboss.arquillian.graphene.bytebuddy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.NamingStrategy;
import net.bytebuddy.TypeCache;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;

/**
 * Thanks to jMock guys for this handy class that wraps all the cglib magic.
 * In particular it workarounds a cglib limitation by allowing to proxy a class even if the misses a no args constructor.
 *
 * @author Mario Fusco
 * @author Sebastian Jancke
 */
@SuppressWarnings("rawtypes")
public class ClassImposterizer {
    private final TypeCache<Class<?>> cache = new TypeCache<>();
    private static final Map<Class<?>, Field> interceptorFields = new ConcurrentHashMap<>();
    public static final String TAG = "ByGraphene";
    private static final CallbackFilter IGNORED_METHODS = new CallbackFilter();
    private final Objenesis objenesis = new ObjenesisStd();
    private static final ClassImposterizer INSTANCE = new ClassImposterizer();
    private static final DefaultNamingPolicy DEFAULT_POLICY = new DefaultNamingPolicy();
    private static final SignedNamingPolicy SIGNED_POLICY = new SignedNamingPolicy();

    protected ClassImposterizer() { }

    private static final class DefaultNamingPolicy extends NamingStrategy.AbstractBase {
        @Override
        protected String name(TypeDescription superClass) {
            return String.format("%s_%s", superClass.getName(), TAG);
        }
    };

    private static final class SignedNamingPolicy extends NamingStrategy.AbstractBase {
        @Override
        protected String name(TypeDescription superClass) {
            return String.format("codegen.%s_%s", superClass.getName(), TAG);
        }
    };

    private static final class CallbackFilter implements ElementMatcher<MethodDescription> {
        public boolean matches(MethodDescription method) {
            return method.isBridge() || isGroovyMethod(method);
        }

        /**
         * Detects whether given method is generated by Groovy
         * https://issues.jboss.org/browse/ARQGRA-446
         */
        private boolean isGroovyMethod(MethodDescription method){
            String[] groovyIgnores = {"$", "this$", "super$", "getMetaClass"};
            Set<String> groovyPrefixes = new HashSet<String>(Arrays.asList(groovyIgnores));
            for (String prefix : groovyPrefixes) {
                if(method.getName().startsWith(prefix)){
                    return true;
                }
            }
            return false;
        }
    };

    protected <T> T imposteriseProtected(MethodInterceptor interceptor, Class<?> mockedType, Class<?>... ancillaryTypes) {
        if (mockedType.isInterface()) {
            return imposteriseInterface(interceptor, mockedType, ancillaryTypes);
        } else {
            return imposteriseClass(interceptor, mockedType, ancillaryTypes);
        }
    }

    @SuppressWarnings("unchecked")
    protected <T> T imposteriseClass(MethodInterceptor interceptor, Class<?> mockedType, Class<?>... ancillaryTypes) {
        setConstructorsAccessible(mockedType, true);
        Class<?> proxyClass = createProxyClass(mockedType, interceptor.getClass(), ancillaryTypes);
        return (T) mockedType.cast(createProxy(proxyClass, interceptor));
    }

    protected <T> T imposteriseInterface(MethodInterceptor interceptor, Class<?> mockedInterface, Class<?>... ancillaryTypes) {

        if (!Modifier.isPublic(mockedInterface.getModifiers())) {
            throw new IllegalArgumentException("Imposterized interface must be public: " + mockedInterface);
        }

        return imposteriseClass(interceptor, mockedInterface, ancillaryTypes);
    }

    private void setConstructorsAccessible(Class<?> mockedType, boolean accessible) {
        for (Constructor<?> constructor : mockedType.getDeclaredConstructors()) {
            constructor.setAccessible(accessible);
        }
    }
    private <T> Class<?> createProxyClass(Class<?> mockedType, Class<?> interceptorType, Class<?>...interfaces) {
        return cache.findOrInsert(interceptorType.getClassLoader(), mockedType,
                () -> createProxyClassInternal(mockedType, interceptorType, interfaces));
    }

    private <T> Class<?> createProxyClassInternal(Class<?> mockedType, Class<?> interceptorType, Class<?>...interfaces) {
        DynamicType.Unloaded unloadedType = new ByteBuddy()
                .with(mockedType.getSigners() != null ? SIGNED_POLICY : DEFAULT_POLICY)
                .subclass(mockedType)
                .implement(interfaces)
                .defineField("__interceptor", MethodInterceptor.class, Visibility.PRIVATE)
                .method(ElementMatchers.not(ElementMatchers.anyOf(IGNORED_METHODS,
                        ElementMatchers.isDeclaredBy(Object.class))))
                .intercept(MethodDelegation.to(interceptorType)).make();
        Class<?> proxyType = unloadedType.load(mockedType.getClassLoader()).getLoaded();
        try {
            Field interceptorField = proxyType.getDeclaredField("__interceptor");
            interceptorField.setAccessible(true);
            interceptorFields.put(proxyType, interceptorField);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        return proxyType;
    }

    private Object createProxy(Class<?> proxyClass, MethodInterceptor interceptor) {
        Object instance = objenesis.newInstance(proxyClass);
        try {
            interceptorFields.get(proxyClass).set(instance, interceptor);
            return instance;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
