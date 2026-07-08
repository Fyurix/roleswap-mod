package com.example.roleswap;

public enum Role {
    BLIND,  // накладывается эффект Blindness, слышит и говорит нормально
    MUTE,   // не может говорить (микрофон глушится сервером), слышит нормально
    DEAF;   // не слышит (входящий звук блокируется), говорить может нормально

    public Role next() {
        Role[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }
}
