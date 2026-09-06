package com.musicdownloader.app

/**
 * ОДИН і той самий User-Agent повинен використовуватись і при діставанні
 * посилання на аудіо-потік (NewPipeDownloaderImpl), і при його відтворенні
 * (ExoPlayer у MainActivity). Якщо ці два клієнти "представляються"
 * по-різному, сервери YouTube (googlevideo.com) можуть мовчки відхиляти
 * запит на відтворення - саме це і спричиняло "нічого не відбувається".
 */
const val NETWORK_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
