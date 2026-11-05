package com.khaled.tvlauncher.time

import java.time.ZoneId

/** Every schedule in the app runs on Damascus time, whatever the TV's own timezone is set to. */
val DAMASCUS: ZoneId = ZoneId.of("Asia/Damascus")
