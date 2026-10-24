package com.abeernch.ledger.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abeernch.ledger.R
import com.abeernch.ledger.data.AppDb
import com.abeernch.ledger.data.Rule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** If a payee name contains a keyword, it gets that category. Your own rules come first. */
@Composable
fun RulesScreen(data: AppData, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var keyword by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    val shown = data.rules.filter { filter.isBlank() || it.keyword.contains(filter, true) || it.category.contains(filter, true) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Category rules", onBack)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = Gutter)) {
            item {
                LedgerCard {
                    Text("New rule", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        keyword, { keyword = it }, singleLine = true,
                        label = { Text("Payee contains, e.g. CHAYE KHANA") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedButton(onClick = { picking = true }, modifier = Modifier.weight(1f)) { Text(category.ifBlank { "Pick category" }) }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            enabled = keyword.isNotBlank() && category.isNotBlank(),
                            onClick = {
                                val k = keyword.trim().uppercase()
                                val c = category
                                keyword = ""
                                scope.launch(Dispatchers.IO) {
                                    val db = AppDb.get(ctx)
                                    db.rules().deleteUserRule(k)
                                    db.rules().insert(Rule(keyword = k, category = c, isUser = true))
                                }
                            }
                        ) { Text("Add") }
                    }
                    Text(
                        "New rules apply to new transactions. To fix an old one, change its category from its detail sheet.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            item {
                OutlinedTextField(filter, { filter = it }, singleLine = true, placeholder = { Text("Search rules") }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
            }
            items(shown, key = { it.id }) { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    CategoryIcon(r.category, 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.keyword, fontWeight = if (r.isUser) FontWeight.SemiBold else FontWeight.Normal)
                        Text(r.category + if (r.isUser) " · yours" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { scope.launch(Dispatchers.IO) { AppDb.get(ctx).rules().delete(r) } }) {
                        Icon(painterResource(R.drawable.ic_delete), "Delete rule")
                    }
                }
                HorizontalDivider(color = LedgerTheme.colors.cardBorder)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (picking) {
        PickerDialog("Category", data.categories, category, { picking = false }, allowNew = "New category") { c ->
            if (c != null) category = c
            picking = false
        }
    }
}
