#!/usr/bin/env bash
set -euo pipefail

# Local fixtures only. Real-service tests and private data access are prohibited.
: "${ANDROID_SERIAL:?Set ANDROID_SERIAL to the intended emulator or test device}"
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
classes=(
  AlbumRowLayoutTest ClientAdditionsTest AnimatedPhotoTest AlbumAiTest AlbumDraftRestoreTest AlbumScanRestoreTest AlbumPathsRestoreTest AlbumMembersRestoreTest AlbumCoverRestoreTest AlbumSearchTest BulkAssetsTest BulkRouteRestoreTest ConfigurationRestoreTest CrossModuleRefreshTest DirectoryBrowserTest
  DuplicatesTest EditHistoryTest FavoriteFilterTest LibrarySearchTest LargeLibraryPagingTest LargeImageDecodeTest LivePhotoTest
  ManagementMotionTest ManagementThemeTest ManagementCloseoutVisualTest MediaBoundsTest MemoriesTest OriginalOnlyFormatTest OriginalVersionCacheTest OriginalDeleteTest PagedViewerTest PhotoEditorTest PhotoEditRestoreTest PhotoTagsRestoreTest PhotoWallApiTest
  PrivateAccessTest RemoteAlbumsTest RemoteViewerTest RemoteMediaGestureTest VideoBufferingTest RemoteWallUiTest SmartFilterTest SmartAlbumDraftRestoreTest
  SystemSettingsTest PasswordRestoreTest ProviderDraftRestoreTest TagRulesTest UnusedTagsTest TagsTest TagDraftRestoreTest TasksTest ScanBatchesTest JobHistoryRestoreTest TasksLayoutTest TaskRouteRestoreTest ThemeTest RemoteSearchUiTest RemoteNavigationUiTest TrashClearTest TrashTest TrashRouteRestoreTest XmpExportsTest XmpRouteRestoreTest
)
qualified=()
for test_class in "${classes[@]}"; do
  if [[ "${PHOTOTUBE_SKIP_AI_TESTS:-0}" == "1" ]]; then
    case "$test_class" in
      AlbumAiTest|ProviderDraftRestoreTest|TasksTest|TasksLayoutTest|TaskRouteRestoreTest) continue ;;
      SystemSettingsTest)
        for method in passwordUnicodePayloadAndVerificationDoNotExpireSession passwordRateLimitPreservesSessionAndDoesNotRetry passwordFormRetainsDraftOnRejectionAndOnlySignsOutOn204 uncertainPasswordResultDoesNotRepeatWriteAndOffersReauthentication missingDiagnosticsExplainsDeploymentAndRefreshCanRecover diagnosticsPreservesUnknownStatisticsAndNeverProbesAi; do
          qualified+=("com.yunai.phototube.SystemSettingsTest#$method")
        done
        continue ;;
    esac
  fi
  qualified+=("com.yunai.phototube.$test_class")
done
class_list="$(IFS=,; echo "${qualified[*]}")"
exec ./gradlew :app:connectedDebugAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.class=$class_list" :app:lintDebug "$@"
