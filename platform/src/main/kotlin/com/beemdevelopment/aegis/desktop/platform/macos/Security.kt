package com.beemdevelopment.aegis.desktop.platform.macos

import com.beemdevelopment.aegis.desktop.platform.SecretStoreException
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import java.io.Closeable

/**
 * A dlopen'd system framework. The [NativeLibrary] handle is the only way to read exported global
 * variables (the `kSec…` constants are variables, not functions); [Native.load] needs the name.
 */
internal class Framework private constructor(private val library: NativeLibrary, private val name: String) {

    fun <T : Library> bind(type: Class<T>): T? = try {
        Native.load(name, type)
    } catch (e: UnsatisfiedLinkError) {
        null
    } catch (e: NoClassDefFoundError) {
        null
    }

    /** The symbol is a variable holding a pointer, so its address is dereferenced once. */
    fun objectConstant(symbol: String): Pointer? = try {
        library.getGlobalVariableAddress(symbol)?.getPointer(0)
    } catch (e: UnsatisfiedLinkError) {
        null
    }

    /** For constants that are structs, such as the dictionary callback tables: no dereference. */
    fun structConstant(symbol: String): Pointer? = try {
        library.getGlobalVariableAddress(symbol)
    } catch (e: UnsatisfiedLinkError) {
        null
    }

    companion object {
        /** Loads the first of [names] that resolves. Pass the full framework path before the bare name. */
        fun load(vararg names: String): Framework? {
            if (!com.sun.jna.Platform.isMac()) {
                return null
            }
            for (name in names) {
                try {
                    return Framework(NativeLibrary.getInstance(name), name)
                } catch (e: UnsatisfiedLinkError) {
                    continue
                } catch (e: NoClassDefFoundError) {
                    return null
                }
            }
            return null
        }
    }
}

/**
 * The slice of CoreFoundation needed for the keychain. Values cross as raw [Pointer]s: the unwrap
 * key passes through here, and a JNA-marshalled `String` would leave copies nothing can zero.
 */
internal interface CoreFoundation : Library {

    fun CFRelease(cf: Pointer)

    /** Takes bytes rather than a JNA `String` to force UTF-8. The array must be NUL-terminated. */
    fun CFStringCreateWithCString(alloc: Pointer?, cStr: ByteArray, encoding: Int): Pointer?

    /** Copies the bytes. */
    fun CFDataCreate(allocator: Pointer?, bytes: Pointer, length: NativeLong): Pointer?

    fun CFDataGetLength(theData: Pointer): NativeLong

    /** Pointer to the data's own storage. Valid until the [CFRelease] that frees the object. */
    fun CFDataGetBytePtr(theData: Pointer): Pointer?

    fun CFDictionaryCreateMutable(
        allocator: Pointer?,
        capacity: NativeLong,
        keyCallBacks: Pointer?,
        valueCallBacks: Pointer?,
    ): Pointer?

    fun CFDictionarySetValue(theDict: Pointer, key: Pointer, value: Pointer)

    fun CFGetTypeID(cf: Pointer): NativeLong

    fun CFDataGetTypeID(): NativeLong

    companion object {
        /** `kCFStringEncodingUTF8`. */
        const val UTF8 = 0x08000100

        val framework: Framework? by lazy {
            Framework.load(
                "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation",
                "CoreFoundation",
            )
        }

        val instance: CoreFoundation? by lazy { framework?.bind(CoreFoundation::class.java) }
    }
}

/**
 * The slice of the Security framework needed for generic password items. `OSStatus` comes back as a
 * plain [Int] so callers can tell "no such item" from a cancelled prompt from a real failure.
 */
internal interface Security : Library {

    fun SecItemAdd(attributes: Pointer, result: PointerByReference?): Int

    /** Blocks on a Touch ID prompt when the matched item carries an access control. */
    fun SecItemCopyMatching(query: Pointer, result: PointerByReference?): Int

    fun SecItemDelete(query: Pointer): Int

    /** Unused: an update cannot change an access control, so replacing a key is delete-then-add. */
    fun SecItemUpdate(query: Pointer, attributesToUpdate: Pointer): Int

    /** `protection` is a `kSecAttrAccessible…` constant, carried by the access control itself. */
    fun SecAccessControlCreateWithFlags(
        allocator: Pointer?,
        protection: Pointer,
        flags: NativeLong,
        error: PointerByReference?,
    ): Pointer?

    companion object {
        const val errSecSuccess = 0

        /** The user dismissed the authentication prompt. */
        const val errSecUserCanceled = -128

        const val errSecUnimplemented = -4
        const val errSecParam = -50

        /** Authentication was attempted and did not succeed. */
        const val errSecAuthFailed = -25293

        const val errSecDuplicateItem = -25299
        const val errSecItemNotFound = -25300

        /** A prompt was required but the process may not show one. */
        const val errSecInteractionNotAllowed = -25308

        /** The data protection keychain refused the process: it is not signed for it. */
        const val errSecMissingEntitlement = -34018

        /** `kSecAccessControlUserPresence`: Touch ID, Apple Watch, or the account password. */
        const val kSecAccessControlUserPresence = 1L

        val framework: Framework? by lazy {
            Framework.load(
                "/System/Library/Frameworks/Security.framework/Security",
                "Security",
            )
        }

        val instance: Security? by lazy { framework?.bind(Security::class.java) }
    }
}

/** The exported constants, resolved once and all or nothing. */
internal class SecConstants private constructor(
    val kSecClass: Pointer,
    val kSecClassGenericPassword: Pointer,
    val kSecAttrService: Pointer,
    val kSecAttrAccount: Pointer,
    val kSecValueData: Pointer,
    val kSecReturnData: Pointer,
    val kSecReturnAttributes: Pointer,
    val kSecMatchLimit: Pointer,
    val kSecMatchLimitOne: Pointer,
    val kSecAttrAccessible: Pointer,
    val kSecAttrAccessibleWhenUnlockedThisDeviceOnly: Pointer,
    val kSecAttrAccessControl: Pointer,
    val kSecUseOperationPrompt: Pointer,
    val kCFBooleanTrue: Pointer,
    val dictionaryKeyCallBacks: Pointer,
    val dictionaryValueCallBacks: Pointer,
    /**
     * macOS 10.15 and later. Only the data protection keychain enforces `kSecAttrAccessControl`; the
     * older file-based keychain hands an item straight back to its creator with no prompt.
     */
    val kSecUseDataProtectionKeychain: Pointer?,
) {
    companion object {
        val instance: SecConstants? by lazy { resolve() }

        private fun resolve(): SecConstants? {
            val cf = CoreFoundation.framework ?: return null
            val sec = Security.framework ?: return null
            return SecConstants(
                kSecClass = sec.objectConstant("kSecClass") ?: return null,
                kSecClassGenericPassword = sec.objectConstant("kSecClassGenericPassword") ?: return null,
                kSecAttrService = sec.objectConstant("kSecAttrService") ?: return null,
                kSecAttrAccount = sec.objectConstant("kSecAttrAccount") ?: return null,
                kSecValueData = sec.objectConstant("kSecValueData") ?: return null,
                kSecReturnData = sec.objectConstant("kSecReturnData") ?: return null,
                kSecReturnAttributes = sec.objectConstant("kSecReturnAttributes") ?: return null,
                kSecMatchLimit = sec.objectConstant("kSecMatchLimit") ?: return null,
                kSecMatchLimitOne = sec.objectConstant("kSecMatchLimitOne") ?: return null,
                kSecAttrAccessible = sec.objectConstant("kSecAttrAccessible") ?: return null,
                kSecAttrAccessibleWhenUnlockedThisDeviceOnly =
                    sec.objectConstant("kSecAttrAccessibleWhenUnlockedThisDeviceOnly") ?: return null,
                kSecAttrAccessControl = sec.objectConstant("kSecAttrAccessControl") ?: return null,
                kSecUseOperationPrompt = sec.objectConstant("kSecUseOperationPrompt") ?: return null,
                kCFBooleanTrue = cf.objectConstant("kCFBooleanTrue") ?: return null,
                dictionaryKeyCallBacks = cf.structConstant("kCFTypeDictionaryKeyCallBacks") ?: return null,
                dictionaryValueCallBacks = cf.structConstant("kCFTypeDictionaryValueCallBacks") ?: return null,
                kSecUseDataProtectionKeychain = sec.objectConstant("kSecUseDataProtectionKeychain"),
            )
        }
    }
}

/** Releases the CoreFoundation objects created while assembling one query, however the call leaves. */
internal class CfScope(private val cf: CoreFoundation, private val constants: SecConstants) : Closeable {
    private val owned = ArrayList<Pointer>()
    private val secrets = ArrayList<Pointer>()

    /** Takes ownership of a freshly created object, throwing if the allocator refused. */
    fun own(pointer: Pointer?, what: String): Pointer {
        val created = pointer ?: throw SecretStoreException("CoreFoundation could not create $what")
        owned.add(created)
        return created
    }

    /** Same, for a `CFData` holding key material: its storage is overwritten before release. */
    fun ownSecret(pointer: Pointer?, what: String): Pointer {
        val created = own(pointer, what)
        secrets.add(created)
        return created
    }

    /** Non-secret text only: ids, service names, prompts. */
    fun string(text: String): Pointer {
        val utf8 = text.toByteArray(Charsets.UTF_8)
        // CFStringCreateWithCString reads up to a NUL, which JNA does not add for a byte array.
        val terminated = utf8.copyOf(utf8.size + 1)
        return own(cf.CFStringCreateWithCString(null, terminated, CoreFoundation.UTF8), "a string")
    }

    /** Standard retain/release callbacks, so it owns its keys and values. Capacity zero is no limit. */
    fun dictionary(): Pointer = own(
        cf.CFDictionaryCreateMutable(
            null,
            NativeLong(0),
            constants.dictionaryKeyCallBacks,
            constants.dictionaryValueCallBacks,
        ),
        "a dictionary",
    )

    override fun close() {
        for (secret in secrets) {
            wipeData(cf, secret)
        }
        secrets.clear()
        for (pointer in owned.asReversed()) {
            cf.CFRelease(pointer)
        }
        owned.clear()
    }
}

/** Sanity bound on a length the keychain reports. */
private const val MAX_SECRET_BYTES = 4096

/** Overwrites the storage of a `CFData` we own that holds key material, before it is released. */
private fun wipeData(cf: CoreFoundation, data: Pointer) {
    try {
        val length = cf.CFDataGetLength(data).toLong()
        val bytes = cf.CFDataGetBytePtr(data)
        if (length > 0 && bytes != null) {
            bytes.setMemory(0, length, 0)
        }
    } catch (e: Throwable) {
        // A failure to wipe must not mask the result of the operation that produced the data.
    }
}

/** Copies a returned `CFData` into a [ByteArray], wipes the framework's copy, and releases it. */
internal fun readSecretData(cf: CoreFoundation, data: Pointer): ByteArray {
    try {
        if (cf.CFGetTypeID(data).toLong() != cf.CFDataGetTypeID().toLong()) {
            throw SecretStoreException("The keychain returned an item that is not data")
        }
        val length = cf.CFDataGetLength(data).toLong()
        if (length <= 0 || length > MAX_SECRET_BYTES) {
            throw SecretStoreException("The keychain returned a key of an implausible size ($length bytes)")
        }
        val bytes = cf.CFDataGetBytePtr(data)
            ?: throw SecretStoreException("The keychain returned data with no contents")
        val copy = bytes.getByteArray(0, length.toInt())
        bytes.setMemory(0, length, 0)
        return copy
    } finally {
        cf.CFRelease(data)
    }
}

/** Copies bytes into native memory that is zeroed before it is released. */
internal inline fun <T> withSecretMemory(bytes: ByteArray, block: (Memory) -> T): T {
    // A zero-length allocation is not portable; callers reject empty keys first.
    val memory = Memory(maxOf(bytes.size, 1).toLong())
    return try {
        memory.write(0, bytes, 0, bytes.size)
        block(memory)
    } finally {
        memory.clear()
        memory.close()
    }
}

internal class KeychainData(val status: Int, val bytes: ByteArray?)

/**
 * Generic password items under one service name. Every operation names the keychain it addresses:
 * macOS has the file-based login keychain and the data protection keychain, which is the only one
 * enforcing `kSecAttrAccessControl`. An item in one is invisible to the other.
 */
internal class KeychainItems(private val service: String) {

    val isAvailable: Boolean
        get() = CoreFoundation.instance != null && Security.instance != null && SecConstants.instance != null

    /** Whether this process may use the data protection keychain depends on signing; see [add]. */
    val supportsAccessControl: Boolean
        get() = isAvailable && SecConstants.instance?.kSecUseDataProtectionKeychain != null

    /** Fails rather than replacing if an item is already there. */
    fun add(account: String, secret: ByteArray, requireUserPresence: Boolean): Int {
        val cf = coreFoundation()
        val security = security()
        val constants = constants()
        val scope = CfScope(cf, constants)
        try {
            val attributes = scope.dictionary()
            addBaseAttributes(cf, constants, scope, attributes, account, dataProtection = requireUserPresence)
            return withSecretMemory(secret) { memory ->
                val data = scope.ownSecret(
                    cf.CFDataCreate(null, memory, NativeLong(secret.size.toLong())),
                    "the key",
                )
                cf.CFDictionarySetValue(attributes, constants.kSecValueData, data)
                if (requireUserPresence) {
                    cf.CFDictionarySetValue(
                        attributes,
                        constants.kSecAttrAccessControl,
                        userPresenceAccessControl(security, constants, scope),
                    )
                } else {
                    // Setting this alongside an access control is rejected, hence the either/or:
                    // the access control carries the same accessibility rule itself.
                    cf.CFDictionarySetValue(
                        attributes,
                        constants.kSecAttrAccessible,
                        constants.kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
                    )
                }
                security.SecItemAdd(attributes, null)
            }
        } finally {
            scope.close()
        }
    }

    /** Prompts when the item has an access control. A null [prompt] leaves macOS its own wording. */
    fun copyData(account: String, prompt: String?, dataProtection: Boolean): KeychainData {
        val cf = coreFoundation()
        val security = security()
        val constants = constants()
        val result = PointerByReference()
        val status: Int
        val scope = CfScope(cf, constants)
        try {
            val query = scope.dictionary()
            addBaseAttributes(cf, constants, scope, query, account, dataProtection)
            cf.CFDictionarySetValue(query, constants.kSecReturnData, constants.kCFBooleanTrue)
            cf.CFDictionarySetValue(query, constants.kSecMatchLimit, constants.kSecMatchLimitOne)
            if (prompt != null) {
                cf.CFDictionarySetValue(query, constants.kSecUseOperationPrompt, scope.string(prompt))
            }
            status = security.SecItemCopyMatching(query, result)
        } finally {
            scope.close()
        }

        if (status != Security.errSecSuccess) {
            result.value?.let { cf.CFRelease(it) }
            return KeychainData(status, null)
        }
        val data = result.value
            ?: throw SecretStoreException("The keychain reported success but returned nothing")
        return KeychainData(status, readSecretData(cf, data))
    }

    /** Asks for attributes rather than data, which is not behind the access control, so it never prompts. */
    fun exists(account: String, dataProtection: Boolean): Int {
        val cf = coreFoundation()
        val security = security()
        val constants = constants()
        val result = PointerByReference()
        val status: Int
        val scope = CfScope(cf, constants)
        try {
            val query = scope.dictionary()
            addBaseAttributes(cf, constants, scope, query, account, dataProtection)
            cf.CFDictionarySetValue(query, constants.kSecReturnAttributes, constants.kCFBooleanTrue)
            cf.CFDictionarySetValue(query, constants.kSecMatchLimit, constants.kSecMatchLimitOne)
            status = security.SecItemCopyMatching(query, result)
        } finally {
            scope.close()
        }
        result.value?.let { cf.CFRelease(it) }
        return status
    }

    fun delete(account: String, dataProtection: Boolean): Int {
        val cf = coreFoundation()
        val security = security()
        val constants = constants()
        val scope = CfScope(cf, constants)
        try {
            val query = scope.dictionary()
            addBaseAttributes(cf, constants, scope, query, account, dataProtection)
            return security.SecItemDelete(query)
        } finally {
            scope.close()
        }
    }

    /** Class, service and account: what identifies one item. */
    private fun addBaseAttributes(
        cf: CoreFoundation,
        constants: SecConstants,
        scope: CfScope,
        dictionary: Pointer,
        account: String,
        dataProtection: Boolean,
    ) {
        cf.CFDictionarySetValue(dictionary, constants.kSecClass, constants.kSecClassGenericPassword)
        cf.CFDictionarySetValue(dictionary, constants.kSecAttrService, scope.string(service))
        cf.CFDictionarySetValue(dictionary, constants.kSecAttrAccount, scope.string(account))
        if (dataProtection) {
            val flag = constants.kSecUseDataProtectionKeychain
                ?: throw SecretStoreException(
                    "This version of macOS cannot enforce an authentication requirement on a keychain item",
                )
            cf.CFDictionarySetValue(dictionary, flag, constants.kCFBooleanTrue)
        }
    }

    /** Requires Touch ID, an unlocked Apple Watch or the account password. */
    private fun userPresenceAccessControl(
        security: Security,
        constants: SecConstants,
        scope: CfScope,
    ): Pointer {
        val error = PointerByReference()
        val control = security.SecAccessControlCreateWithFlags(
            null,
            constants.kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            NativeLong(Security.kSecAccessControlUserPresence),
            error,
        )
        error.value?.let { scope.own(it, "an error") }
        return scope.own(control, "an authentication requirement")
    }

    private fun coreFoundation(): CoreFoundation = CoreFoundation.instance
        ?: throw SecretStoreException("The CoreFoundation framework could not be loaded")

    private fun security(): Security = Security.instance
        ?: throw SecretStoreException("The Security framework could not be loaded")

    private fun constants(): SecConstants = SecConstants.instance
        ?: throw SecretStoreException("The Security framework does not export the constants the keychain needs")
}
