package com.nexusforge.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusforge.app.data.organizer.ProjectKind
import com.nexusforge.app.ui.theme.MonoFamily

private class KindStyle(val start: Color, val end: Color, val icon: ImageVector? = null, val text: String = "")

private fun styleOf(kind: ProjectKind): KindStyle = when (kind) {
    ProjectKind.COMPOSE -> KindStyle(Color(0xFF34D399), Color(0xFF059669), Icons.Default.Widgets)
    ProjectKind.ANDROID -> KindStyle(Color(0xFF4ADE80), Color(0xFF16A34A), Icons.Default.Android)
    ProjectKind.KOTLIN_LIB -> KindStyle(Color(0xFFA78BFA), Color(0xFF7C3AED), text = "Kt")
    ProjectKind.KOTLIN -> KindStyle(Color(0xFFC084FC), Color(0xFF9333EA), text = "Kt")
    ProjectKind.JAVA -> KindStyle(Color(0xFFF87171), Color(0xFFDC2626), text = "Jv")
    ProjectKind.WEB -> KindStyle(Color(0xFFFB923C), Color(0xFFEA580C), Icons.Default.Language)
    ProjectKind.NODE -> KindStyle(Color(0xFFFACC15), Color(0xFFCA8A04), text = "JS")
    ProjectKind.PYTHON -> KindStyle(Color(0xFF60A5FA), Color(0xFF2563EB), text = "Py")
    ProjectKind.DOCS -> KindStyle(Color(0xFF94A3B8), Color(0xFF64748B), Icons.Default.Description)
    ProjectKind.BLANK -> KindStyle(Color(0xFF52525B), Color(0xFF3F3F46), Icons.Default.FolderOpen)
    ProjectKind.OTHER -> KindStyle(Color(0xFF71717A), Color(0xFF52525B), Icons.Default.Folder)
}

/** The accent colour of a project type, for bars and highlights. */
fun kindColor(kind: ProjectKind): Color = styleOf(kind).end

/** A coloured rounded tile that makes a project's type recognisable at a glance in a list. */
@Composable
fun ProjectKindBadge(kind: ProjectKind, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val st = styleOf(kind)
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(Brush.linearGradient(listOf(st.start, st.end))),
        contentAlignment = Alignment.Center
    ) {
        if (st.icon != null) {
            Icon(st.icon, contentDescription = kind.label, tint = Color.White, modifier = Modifier.size(size * 0.54f))
        } else {
            Text(st.text, color = Color.White, fontFamily = MonoFamily, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp)
        }
    }
}
