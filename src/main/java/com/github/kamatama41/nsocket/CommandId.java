package com.github.kamatama41.nsocket;

import java.util.Locale;

interface CommandId {
    // FooBarCommand -> fooBar
    default String getId() {
        String idWithFirstUpperChar = this.getClass().getSimpleName().split("Command$")[0];
        String firstChar = Character.toString(idWithFirstUpperChar.charAt(0)).toLowerCase(Locale.US);
        return firstChar + idWithFirstUpperChar.substring(1);
    }
}
