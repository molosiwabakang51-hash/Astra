"""No repeated Qwen SHA on Activity recreation, without trusting replaced bytes."""
from pathlib import Path
import unittest

JAVA = Path(__file__).resolve().parents[2] / "android/app/src/main/java/com/astra/agent"
BRAIN = (JAVA / "AstraOnDeviceBrain.kt").read_text(encoding="utf-8")
CACHE = (JAVA / "AstraVerifiedModelCache.kt").read_text(encoding="utf-8")
STORAGE = (JAVA / "AstraModelStorage.kt").read_text(encoding="utf-8")


class ProcessVerifiedModelCacheSourceTest(unittest.TestCase):
    def test_all_brains_use_in_memory_process_shared_file_identity_cache(self):
        self.assertIn("private val verifiedModels = AstraProcessVerifiedModelCache.cache", BRAIN)
        self.assertNotIn("private val verifiedModels = AstraVerifiedModelCache()", BRAIN)
        self.assertIn("internal object AstraProcessVerifiedModelCache", CACHE)
        self.assertIn("val cache = AstraVerifiedModelCache()", CACHE)
        self.assertNotIn("SharedPreferences", CACHE)
        self.assertNotIn("FileOutputStream", CACHE)

    def test_verification_is_bound_to_inode_size_mtime_and_expected_digest(self):
        self.assertIn("BasicFileAttributes::class.java", CACHE)
        self.assertIn("LinkOption.NOFOLLOW_LINKS", CACHE)
        self.assertIn("file.lastModified() == modifiedAt", CACHE)
        self.assertIn("file.length() == size", CACHE)
        self.assertIn("fileIdentity(file) == identity", CACHE)
        self.assertIn("it.sha256.equals(expectedSha256, ignoreCase = true)", CACHE)
        self.assertIn("entry = null", CACHE)

    def test_production_verified_file_install_and_recovery_invalidate_cache(self):
        self.assertEqual(2, STORAGE.count("AstraProcessVerifiedModelCache.cache.invalidate()"))
        self.assertIn("if (!target.isFile && backup.isFile)", STORAGE)
        self.assertIn("verifiedTemporary.renameTo(target)", STORAGE)
        self.assertIn("@Synchronized\n    fun installVerified", STORAGE)
        self.assertIn("@Synchronized\n    fun activeModel", STORAGE)

    def test_native_session_remains_exclusive_during_sha_verification(self):
        start = BRAIN.index("suspend fun reply(")
        end = BRAIN.index("private suspend fun ensureModel(", start)
        excerpt = BRAIN[start:end]
        self.assertIn("AstraModelOperationGate.withNativeSession", excerpt)
        self.assertIn("ensureModel(", excerpt)
        self.assertLess(excerpt.index("withNativeSession"), excerpt.index("ensureModel("))


if __name__ == "__main__":
    unittest.main()
