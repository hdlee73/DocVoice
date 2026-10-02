package com.docvoice.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
