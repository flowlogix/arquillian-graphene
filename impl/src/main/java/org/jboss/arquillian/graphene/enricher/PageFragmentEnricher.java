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
package org.jboss.arquillian.graphene.enricher;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.FieldValue;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperMethod;
import net.bytebuddy.implementation.bind.annotation.This;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.graphene.GrapheneElement;
import org.jboss.arquillian.graphene.bytebuddy.MethodInterceptor;
import org.jboss.arquillian.graphene.context.GrapheneContext;
import org.jboss.arquillian.graphene.enricher.exception.PageFragmentInitializationException;
import org.jboss.arquillian.graphene.findby.FindByUtilities;
import org.jboss.arquillian.graphene.fragment.Root;
import org.jboss.arquillian.graphene.proxy.GrapheneContextualHandler;
import org.jboss.arquillian.graphene.proxy.GrapheneProxy;
import org.jboss.arquillian.graphene.proxy.GrapheneProxy.FutureTarget;
import org.jboss.arquillian.graphene.proxy.GrapheneProxyInstance;
import org.jboss.arquillian.graphene.spi.configuration.GrapheneConfiguration;
import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

/**
 * Enricher injecting page fragments ({@link FindBy} annotation is used) to the fields of the given object.
 *
 * @author <a href="mailto:jhuska@redhat.com">Juraj Huska</a>
 * @author <a href="mailto:jpapouse@redhat.com">Jan Papousek</a>
 */
public class PageFragmentEnricher extends AbstractSearchContextEnricher {
    public static class Interceptor implements MethodInterceptor {
        private final WebElement root;
        private final Class<?> webElementType;

        public Interceptor(WebElement root, Class<?> webElementType) {
            this.root = root;
            this.webElementType = webElementType;
        }

        @RuntimeType
        public static Object intercept(@This Object self,
                                       @Origin Method method,
                                       @FieldValue("__interceptor") MethodInterceptor methodInterceptor,
                                       @AllArguments Object[] args,
                                       @SuperMethod(nullIfImpossible = true) Method superMethod) throws Throwable {
            if (methodInterceptor instanceof GrapheneContextualHandler) {
                return GrapheneContextualHandler.intercept(self, methodInterceptor, method, args);
            }
            Interceptor interceptor = (Interceptor) methodInterceptor;
            if (method.getName().equals("unwrap")) {
                return interceptor.root;
            }
            List<Method> webElementMethods = Arrays.asList(interceptor.webElementType.getMethods());
            if (webElementMethods.contains(method)) {
                try {
                    return method.invoke(interceptor.root, args);
                } catch (InvocationTargetException e) {
                    // unwrap original exception
                    throw e.getCause();
                }
            } else {
                return superMethod.invoke(self, args);
            }
        }
    }

    @Inject
    private Instance<GrapheneConfiguration> configuration;

    public PageFragmentEnricher() {
    }

    // because of testing
    public PageFragmentEnricher(Instance<GrapheneConfiguration> configuration) {
        this.configuration = configuration;
    }

    @Override
    public void enrich(final SearchContext searchContext, Object target) {
        List<Field> fields = FindByUtilities.getListOfFieldsAnnotatedWithFindBys(target);
        for (Field field : fields) {
            GrapheneContext grapheneContext = searchContext == null ? null : ((GrapheneProxyInstance) searchContext)
                .getGrapheneContext();
            final SearchContext localSearchContext;
            if (grapheneContext == null) {
                grapheneContext = GrapheneContext.getContextFor(ReflectionHelper.getQualifier(field.getAnnotations()));
                localSearchContext = grapheneContext.getWebDriver(SearchContext.class);
            } else {
                localSearchContext = searchContext;
            }
            // Page fragment
            if (isPageFragmentClass(field.getType(), target)) {
                setupPageFragment(localSearchContext, target, field);
                // List<Page fragment>
            } else {
                try {
                    if (field.getType().isAssignableFrom(List.class) && isPageFragmentClass(getListType(field), target)) {
                        setupPageFragmentList(localSearchContext, target, field);
                    }
                } catch (ClassNotFoundException e) {
                    throw new PageFragmentInitializationException(e.getMessage(), e);
                }
            }
        }
    }

    @Override
    public Object[] resolve(SearchContext searchContext, Method method, Object[] resolvedParams) {
        return resolvedParams;
    }

    protected final boolean isPageFragmentClass(Class<?> clazz, Object target) {
        if(Modifier.isInterface(clazz.getModifiers())) {
            return false;
        }

        Class<?> outerClass = clazz.getDeclaringClass();

        if (outerClass == null || Modifier.isStatic(clazz.getModifiers())) {
            // check whether there is an empty constructor
            return ReflectionHelper.hasConstructor(clazz);
        } else {
            // check whether there is an empty constructor with outer class
            return ReflectionHelper.hasConstructor(clazz, outerClass);
        }
    }

    protected final <T> List<T> createPageFragmentList(final Class<T> clazz, final SearchContext searchContext, final By rootBy) {
        GrapheneContext grapheneContext = ((GrapheneProxyInstance) searchContext).getGrapheneContext();
        List<T> result = GrapheneProxy.getProxyForFutureTarget(grapheneContext, new FutureTarget() {
            @Override
            public Object getTarget() {
                List<WebElement> elements = searchContext.findElements(rootBy);
                List<T> fragments = new ArrayList<T>();
                for (int i = 0; i < elements.size(); i++) {
                    fragments.add(createPageFragment(clazz, WebElementUtils.findElementLazily(rootBy, searchContext, i)));
                }
                return fragments;
            }
        }, List.class);
        return result;
    }

    public static <T> T createPageFragment(Class<T> clazz, final WebElement root) {
        try {
            GrapheneContext grapheneContext = ((GrapheneProxyInstance) root).getGrapheneContext();
            T pageFragment = null;

            if (Modifier.isFinal(clazz.getModifiers())) {
                throw new PageFragmentInitializationException("Page Fragment must not be final class. It is, your "
                    + clazz + ", declared in: " + clazz);
            }

            Class<?> webElementInterface = extractWebElementType(clazz);
            if (webElementInterface != null) {
                pageFragment = createProxyDelegatingToRoot(root, clazz, webElementInterface);
            } else {
                pageFragment = instantiate(clazz);
            }
            List<Field> roots = new LinkedList<Field>();
            roots.addAll(ReflectionHelper.getFieldsWithAnnotation(clazz, Root.class));
            if (roots.size() > 1) {
                throw new PageFragmentInitializationException("The Page Fragment " + NEW_LINE + pageFragment.getClass()
                    + NEW_LINE + " can not have more than one field annotated with Root annotation!"
                    + "Your fields with @Root annotation: " + roots + NEW_LINE);
            }
            if (roots.size() == 1) {
                setValue(roots.get(0), pageFragment, root);
            }
            enrichRecursively(root, pageFragment);
            T proxy = GrapheneProxy.getProxyForHandler(GrapheneContextualHandler.forTarget(grapheneContext, pageFragment),
                clazz);
            enrichRecursively(root, proxy); // because of possibility of direct access to attributes from test class
            return proxy;
        } catch (NoSuchMethodException ex) {
            throw new PageFragmentInitializationException(" Check whether declared Page Fragment has no argument constructor!",
                ex);
        } catch (IllegalAccessException ex) {
            throw new PageFragmentInitializationException(
                " Check whether declared Page Fragment has public no argument constructor!", ex);
        } catch (InstantiationException ex) {
            throw new PageFragmentInitializationException(
                " Check whether you did not declare Page Fragment with abstract type!", ex);
        } catch (Exception ex) {
            throw new PageFragmentInitializationException(ex);
        }
    }

    private static Class<?> extractWebElementType(Class<?> clazz) {
        List<Class<?>> interfaces = Arrays.asList(clazz.getInterfaces());
        if (interfaces.contains(GrapheneElement.class)) {
            return GrapheneElement.class;
        } else if (interfaces.contains(WebElement.class)) {
            return WebElement.class;
        } else {
            Class<?> superclass = clazz.getSuperclass();
            if (!Object.class.equals(superclass)) {
                return extractWebElementType(superclass);
            }
        }
        return null;
    }

    private static <T> T createProxyDelegatingToRoot(final WebElement root, Class<T> clazz, final Class<?> webElementType) {
        return ClassImposterizer.INSTANCE.imposterise(new Interceptor(root, webElementType), clazz, webElementType);
    }

    protected final void setupPageFragmentList(SearchContext searchContext, Object target, Field field)
        throws ClassNotFoundException {
        GrapheneContext grapheneContext = ((GrapheneProxyInstance) searchContext).getGrapheneContext();
        // the by retrieved in this way is never null, by default it is ByIdOrName using field name
        By rootBy = FindByUtilities.getCorrectBy(field, configuration.get().getDefaultElementLocatingStrategy());
        List<?> pageFragments = createPageFragmentList(getListType(field), searchContext, rootBy);
        setValue(field, target, pageFragments);
    }

    protected final void setupPageFragment(SearchContext searchContext, Object target, Field field) {
        GrapheneContext grapheneContext = ((GrapheneProxyInstance) searchContext).getGrapheneContext();
        // the by retrieved in this way is never null, by default it is ByIdOrName using field name
        By rootBy = FindByUtilities.getCorrectBy(field, configuration.get().getDefaultElementLocatingStrategy());
        WebElement root = WebElementUtils.findElementLazily(rootBy, searchContext);
        Object pageFragment = createPageFragment(field.getType(), root);
        setValue(field, target, pageFragment);
    }

    @Override
    public int getPrecedence() {
        return 1;
    }
}
