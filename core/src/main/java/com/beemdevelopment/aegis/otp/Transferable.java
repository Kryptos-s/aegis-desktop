package com.beemdevelopment.aegis.otp;

import com.beemdevelopment.aegis.util.Uri;

public interface Transferable {
    Uri getUri() throws GoogleAuthInfoException;
}
