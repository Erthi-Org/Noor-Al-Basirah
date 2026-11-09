package com.nexvary.hifzai.learning

data class VerifiedVerse(val chapter: Int, val verse: Int, val text: String) {
    val id: Int get() = chapter * 1000 + verse
}

