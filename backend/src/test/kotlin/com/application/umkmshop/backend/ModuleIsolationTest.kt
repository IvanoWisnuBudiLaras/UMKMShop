package com.application.umkmshop.backend

import com.application.umkmshop.backend.oauth.JwtService
import com.application.umkmshop.backend.oauth.OAuthService
import com.application.umkmshop.backend.worker.NotificationWorker
import org.junit.Test
import kotlin.test.assertFalse

class ModuleIsolationTest {

    @Test
    fun `worker module should not have access to OAuth private keys`() {
        // Mendapatkan semua fields dari NotificationWorker via reflection
        val workerFields = NotificationWorker::class.java.declaredFields
        
        // Memastikan tidak ada field yang bertipe JwtService atau OAuthService
        // atau yang memiliki kata kunci 'privateKey' atau 'signingKey'
        for (field in workerFields) {
            val typeName = field.type.simpleName
            assertFalse(
                typeName == "JwtService" || typeName == "OAuthService",
                "NotificationWorker should not depend on OAuth services directly: found $typeName"
            )
            
            val fieldName = field.name.lowercase()
            assertFalse(
                fieldName.contains("privatekey") || fieldName.contains("signingkey"),
                "NotificationWorker should not have fields related to keys: found ${field.name}"
            )
        }
    }
}
