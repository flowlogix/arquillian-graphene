/**
 * JBoss, Home of Professional Open Source
 * Copyright 2013, Red Hat, Inc. and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.arquillian.graphene.intercept;

import net.bytebuddy.implementation.bind.annotation.Empty;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import org.jboss.arquillian.graphene.bytebuddy.MethodInterceptor;

/**
 * Utility class with helper methods for building interceptors using {@link InterceptorBuilder}.
 *
 * @author Lukas Fryc
 */
public final class Interceptors {
    public static class Interceptor implements MethodInterceptor {
        @RuntimeType
        public static Object intercept(@Empty Object defaultValue) throws Throwable {
            return defaultValue;
        }
    }

    public static <T> T any(Class<T> type) {
        return (T) ClassImposterizer.INSTANCE.imposterise(new Interceptor(), type);
    }
}
