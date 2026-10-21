package com.deepseek.dsh.plugin.nativehost

import com.deepseek.dsh.plugin.nativesdk.pages.PageContent
import com.deepseek.dsh.plugin.nativesdk.pages.PageEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pages contributed by companion APKs that were successfully loaded in this process.
 *
 * A discovered APK is not enough to take over a Web settings section: its ABI/signature
 * checks and class loading must have succeeded, and it must have registered a page whose
 * id matches the Web `settings.section` id. The registry is replaced after every package
 * rescan so uninstalling or disabling a companion also removes its native route.
 */
data class NativePageRegistration(
    val pluginId: String,
    val jsPackage: String?,
    val pages: List<PageContent>,
)

object NativePageRegistry {
    private val mutableRegistrations = MutableStateFlow<List<NativePageRegistration>>(emptyList())

    /** Observed by Compose so a package install/uninstall updates an open settings page. */
    val registrations: StateFlow<List<NativePageRegistration>> = mutableRegistrations.asStateFlow()

    fun replaceAll(registrations: List<NativePageRegistration>) {
        mutableRegistrations.value = registrations
    }

    /** Resolve the exact Web component (`<package>#<settings.section id>`) to a native page. */
    fun find(componentId: String): PageContent? =
        find(componentId, mutableRegistrations.value)

    fun find(
        componentId: String,
        registrations: List<NativePageRegistration>,
    ): PageContent? {
        val separator = componentId.lastIndexOf('#')
        if (separator <= 0 || separator == componentId.lastIndex) return null
        val owner = componentId.substring(0, separator)
        val pageId = componentId.substring(separator + 1)

        return registrations.firstNotNullOfOrNull { registration ->
            if (!registration.owns(owner)) return@firstNotNullOfOrNull null
            registration.pages.firstOrNull { page ->
                page.entry.placement == PageEntry.Placement.SETTINGS && page.entry.id == pageId
            }
        }
    }

    private fun NativePageRegistration.owns(owner: String): Boolean {
        if (owner == pluginId || owner == jsPackage) return true
        val npmName = jsPackage
            ?.takeUnless { it.contains("://") || it.startsWith("git+") }
            ?.substringBeforeLast('@')
            ?.takeIf { it.isNotBlank() }
        return owner == npmName
    }
}
