/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.js.runtime.builtins;

import java.util.Locale;
import java.util.Objects;

import com.ibm.icu.text.DisplayContext;
import com.ibm.icu.text.LocaleDisplayNames;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.js.builtins.intl.DisplayNamesFunctionBuiltins;
import com.oracle.truffle.js.builtins.intl.DisplayNamesPrototypeBuiltins;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.objects.JSAttributes;
import com.oracle.truffle.js.runtime.objects.JSNonProxyObject;
import com.oracle.truffle.js.runtime.objects.JSObject;
import com.oracle.truffle.js.runtime.objects.JSObjectUtil;
import com.oracle.truffle.js.runtime.objects.Undefined;
import com.oracle.truffle.js.runtime.util.IntlUtil;

public final class JSDisplayNames extends JSNonProxy implements JSConstructorFactory.Default.WithFunctions, PrototypeSupplier {

    public static final String CLASS_NAME = "DisplayNames";
    public static final String PROTOTYPE_NAME = "DisplayNames.prototype";

    public static final JSDisplayNames INSTANCE = new JSDisplayNames();

    public static final class Instance extends JSNonProxyObject {
        private final InternalState internalState;

        protected Instance(Shape shape, InternalState internalState) {
            super(shape);
            this.internalState = Objects.requireNonNull(internalState);
        }

        public InternalState getInternalState() {
            return internalState;
        }
    }

    private JSDisplayNames() {
    }

    public static boolean isJSDisplayNames(Object obj) {
        return JSObject.isJSDynamicObject(obj) && isJSDisplayNames((DynamicObject) obj);
    }

    public static boolean isJSDisplayNames(DynamicObject obj) {
        return isInstance(obj, INSTANCE);
    }

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    @Override
    public String getClassName(DynamicObject object) {
        return getClassName();
    }

    @Override
    public DynamicObject createPrototype(JSRealm realm, DynamicObject ctor) {
        JSContext ctx = realm.getContext();
        DynamicObject displayNamesPrototype = JSObjectUtil.createOrdinaryPrototypeObject(realm);
        JSObjectUtil.putConstructorProperty(ctx, displayNamesPrototype, ctor);
        JSObjectUtil.putFunctionsFromContainer(realm, displayNamesPrototype, DisplayNamesPrototypeBuiltins.BUILTINS);
        JSObjectUtil.putToStringTag(displayNamesPrototype, "Intl.DisplayNames");
        return displayNamesPrototype;
    }

    @Override
    public Shape makeInitialShape(JSContext ctx, DynamicObject prototype) {
        return JSObjectUtil.getProtoChildShape(prototype, INSTANCE, ctx);
    }

    public static JSConstructor createConstructor(JSRealm realm) {
        return INSTANCE.createConstructorAndPrototype(realm, DisplayNamesFunctionBuiltins.BUILTINS);
    }

    public static DynamicObject create(JSContext context) {
        InternalState state = new InternalState();
        JSRealm realm = context.getRealm();
        JSObjectFactory factory = context.getDisplayNamesFactory();
        Instance obj = new Instance(factory.getShape(realm), state);
        factory.initProto(obj, realm);
        assert isJSDisplayNames(obj);
        return obj;
    }

    public static class InternalState {
        String locale;
        String style;
        String type;
        String fallback;
        LocaleDisplayNames displayNames;

        DynamicObject toResolvedOptionsObject(JSContext context) {
            DynamicObject result = JSOrdinary.create(context);
            JSObjectUtil.defineDataProperty(result, IntlUtil.LOCALE, locale, JSAttributes.getDefault());
            JSObjectUtil.defineDataProperty(result, IntlUtil.STYLE, style, JSAttributes.getDefault());
            JSObjectUtil.defineDataProperty(result, IntlUtil.TYPE, type, JSAttributes.getDefault());
            JSObjectUtil.defineDataProperty(result, IntlUtil.FALLBACK, fallback, JSAttributes.getDefault());
            return result;
        }
    }

    @TruffleBoundary
    public static void setupInternalState(JSContext ctx, InternalState state, String[] locales, String optStyle, String optType, String optFallback) {
        Locale selectedLocale = IntlUtil.selectedLocale(ctx, locales);
        Locale strippedLocale = selectedLocale.stripExtensions();
        if (strippedLocale.toLanguageTag().equals(IntlUtil.UND)) {
            selectedLocale = ctx.getLocale();
            strippedLocale = selectedLocale.stripExtensions();
        }
        state.locale = strippedLocale.toLanguageTag();
        state.style = optStyle;
        state.type = optType;
        state.fallback = optFallback;
        DisplayContext fallbackCtx = fallbackDisplayContext(optFallback);
        DisplayContext styleCtx = styleDisplayContext(optStyle);
        state.displayNames = LocaleDisplayNames.getInstance(strippedLocale, styleCtx, fallbackCtx);
    }

    private static DisplayContext fallbackDisplayContext(String optFallback) {
        return IntlUtil.NONE.equals(optFallback) ? DisplayContext.NO_SUBSTITUTE : DisplayContext.SUBSTITUTE;
    }

    private static DisplayContext styleDisplayContext(String optStyle) {
        return IntlUtil.LONG.equals(optStyle) ? DisplayContext.LENGTH_FULL : DisplayContext.LENGTH_SHORT;
    }

    @TruffleBoundary
    public static DynamicObject resolvedOptions(JSContext context, DynamicObject displayNamesObject) {
        InternalState state = getInternalState(displayNamesObject);
        return state.toResolvedOptionsObject(context);
    }

    @TruffleBoundary
    public static Object of(DynamicObject displayNamesObject, String code) {
        InternalState state = getInternalState(displayNamesObject);
        String type = state.type;
        LocaleDisplayNames displayNames = state.displayNames;
        String result;
        switch (type) {
            case IntlUtil.LANGUAGE:
                IntlUtil.ensureIsStructurallyValidLanguageTag(code);
                result = displayNames.localeDisplayName(code);
                break;
            case IntlUtil.REGION:
                IntlUtil.ensureIsStructurallyValidRegionSubtag(code);
                result = displayNames.regionDisplayName(code);
                break;
            case IntlUtil.SCRIPT:
                IntlUtil.ensureIsStructurallyValidScriptSubtag(code);
                result = displayNames.scriptDisplayName(code);
                break;
            case IntlUtil.CURRENCY:
                IntlUtil.ensureIsWellFormedCurrencyCode(code);
                result = displayNames.keyValueDisplayName(IntlUtil.CURRENCY, code);
                break;
            default:
                throw Errors.shouldNotReachHere(type);
        }
        return (result == null) ? Undefined.instance : result;
    }

    public static InternalState getInternalState(DynamicObject displayNamesObject) {
        assert isJSDisplayNames(displayNamesObject);
        return ((Instance) displayNamesObject).getInternalState();
    }

    @Override
    public DynamicObject getIntrinsicDefaultProto(JSRealm realm) {
        return realm.getDisplayNamesPrototype();
    }

}
