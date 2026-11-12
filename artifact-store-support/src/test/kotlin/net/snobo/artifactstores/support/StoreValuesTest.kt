package net.snobo.artifactstores.support

import kotlin.test.Test
import kotlin.test.assertEquals

class StoreValuesTest {
    @Test
    fun `derives the property and environment names from the instance name`() {
        val names = StoreFieldNames("my-corp.v2")

        assertEquals("artifactStore.my-corp.v2.username", names.property(StoreField.USERNAME))
        assertEquals("ARTIFACT_STORE_MY_CORP_V2_USERNAME", names.environment(StoreField.USERNAME))
        assertEquals("ARTIFACT_STORE_MY_CORP_V2_HEADER_VALUE", names.environment(StoreField.HEADER_VALUE))
        assertEquals("artifactStore.my-corp.v2.releasesRepository", names.property(StoreField.RELEASES_REPOSITORY))
        assertEquals(
            "ARTIFACT_STORE_MY_CORP_V2_SNAPSHOTS_REPOSITORY",
            names.environment(StoreField.SNAPSHOTS_REPOSITORY),
        )
    }
}
