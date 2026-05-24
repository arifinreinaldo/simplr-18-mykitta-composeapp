package com.simplr.mykitta2.domain

data class Banner(
    val bannerId: String,
    val bannerName: String,
    val bannerImg: String,
    val principalId: String,
    val startDate: String,
    val endDate: String,
)
