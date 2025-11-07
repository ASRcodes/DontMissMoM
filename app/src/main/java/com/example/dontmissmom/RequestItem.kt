package com.example.dontmissmom

data class RequestItem(
    val id: String,
    val fromUid: String,
    val fromUsername: String,
    val fromPhone: String,
    val toUid: String,
    val toPhone: String
)
