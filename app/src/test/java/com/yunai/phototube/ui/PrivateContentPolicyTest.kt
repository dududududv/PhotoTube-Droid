package com.yunai.phototube.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateContentPolicyTest {
    @Test
    fun dedicatedPrivateScopesAlwaysProtectTheirVisibleContent() {
        assertTrue(policy(destination = ContentDestination.Private))
        assertTrue(policy(destination = ContentDestination.Duplicates, duplicatesPrivateScope = true))
        assertTrue(policy(destination = ContentDestination.XmpExport, xmpPrivateScope = true))
        assertFalse(policy(destination = ContentDestination.Duplicates, duplicatesPrivateScope = false))
        assertFalse(policy(destination = ContentDestination.XmpExport, xmpPrivateScope = false))
    }

    @Test
    fun viewerProtectsPrivateAssetRegardlessOfItsEntryPage() {
        listOf(
            ContentDestination.Photos,
            ContentDestination.AlbumDetail,
            ContentDestination.Archived,
            ContentDestination.Trash,
            ContentDestination.Search,
        ).forEach { returnDestination ->
            assertTrue(
                policy(
                    destination = ContentDestination.Viewer,
                    viewerReturnDestination = returnDestination,
                    viewerAssetPrivate = true,
                ),
            )
        }
    }

    @Test
    fun viewerKeepsProtectionWhilePrivateSourceAssetIsLoading() {
        assertTrue(
            policy(
                destination = ContentDestination.Viewer,
                viewerReturnDestination = ContentDestination.AlbumDetail,
                viewerAssetPrivate = null,
            ),
        )
        assertTrue(
            policy(
                destination = ContentDestination.Viewer,
                viewerReturnDestination = ContentDestination.Private,
            ),
        )
        assertTrue(
            policy(
                destination = ContentDestination.Viewer,
                viewerReturnDestination = ContentDestination.Duplicates,
                duplicatesPrivateScope = true,
            ),
        )
    }

    @Test
    fun serverConfirmedPublicViewerAndHiddenPrivateFactDoNotProtectUnrelatedPages() {
        assertFalse(
            policy(
                destination = ContentDestination.Viewer,
                viewerReturnDestination = ContentDestination.AlbumDetail,
                viewerAssetPrivate = false,
            ),
        )
        assertFalse(
            policy(
                destination = ContentDestination.Photos,
                viewerAssetPrivate = true,
            ),
        )
    }

    private fun policy(
        destination: ContentDestination,
        viewerReturnDestination: ContentDestination = ContentDestination.Photos,
        viewerAssetPrivate: Boolean? = false,
        duplicatesPrivateScope: Boolean = false,
        xmpPrivateScope: Boolean = false,
    ) = shouldProtectPrivateContent(
        destination = destination,
        viewerReturnDestination = viewerReturnDestination,
        viewerAssetPrivate = viewerAssetPrivate,
        duplicatesPrivateScope = duplicatesPrivateScope,
        xmpPrivateScope = xmpPrivateScope,
    )
}
