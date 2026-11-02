package com.adblocker.ytmusic.ui.shell

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adblocker.ytmusic.R
import com.adblocker.ytmusic.domain.navigation.YtmTab

/** Gris del mini-reproductor de la web: barra y mini-reproductor se ven como una sola pieza. */
val YtmBarColor = Color(0xFF212121)

private data class BarItem(
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    @DrawableRes val selectedIcon: Int,
)

private val TAB_ITEMS = mapOf(
    YtmTab.HOME to BarItem(R.string.tab_home, R.drawable.ic_tab_home_outline, R.drawable.ic_tab_home),
    YtmTab.EXPLORE to BarItem(R.string.tab_explore, R.drawable.ic_tab_explore_outline, R.drawable.ic_tab_explore),
    YtmTab.SEARCH to BarItem(R.string.tab_search, R.drawable.ic_tab_search, R.drawable.ic_tab_search),
    YtmTab.LIBRARY to BarItem(R.string.tab_library, R.drawable.ic_tab_library_outline, R.drawable.ic_tab_library),
)

private val BLOCKER_ITEM = BarItem(R.string.tab_blocker, R.drawable.ic_tab_blocker_outline, R.drawable.ic_tab_blocker)

/**
 * Barra inferior al estilo de la app oficial: iconos y etiquetas blancos, la pestaña activa con
 * el icono relleno y sin "pildora" de Material. Donde la oficial pone "Actualizar", aqui va el
 * bloqueador.
 */
@Composable
fun YtmBottomBar(
    selectedTab: YtmTab,
    blockerOpen: Boolean,
    onTabSelected: (YtmTab) -> Unit,
    onBlockerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(YtmBarColor),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for ((tab, item) in TAB_ITEMS) {
            BarButton(
                item = item,
                selected = tab == selectedTab && !blockerOpen,
                onClick = { onTabSelected(tab) },
            )
        }
        BarButton(item = BLOCKER_ITEM, selected = blockerOpen, onClick = onBlockerClick)
    }
}

@Composable
private fun RowScope.BarButton(item: BarItem, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(if (selected) item.selectedIcon else item.icon),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(item.label),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Preview
@Composable
private fun YtmBottomBarPreview() {
    YtmBottomBar(selectedTab = YtmTab.HOME, blockerOpen = false, onTabSelected = {}, onBlockerClick = {})
}
