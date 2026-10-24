package com.abeernch.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.abeernch.ledger.R
import com.abeernch.ledger.data.AppDb
import com.abeernch.ledger.data.Txn
import com.abeernch.ledger.logic.Balances
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** One fund or goal: its total and every transaction assigned to it. */
@Composable
fun FundDetailScreen(fundId: Long, data: AppData, onBack: () -> Unit, onOpenTxn: (Txn) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val f = data.fund(fundId)
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (f == null) {
        Column { ScreenHeader("Fund", onBack); EmptyState("Not found", "This fund was deleted.") }
        return
    }
    val isGoal = f.kind == "GOAL"
    val value = if (isGoal) Balances.goalSaved(f, data.txns) else Balances.fundSpent(f, data.txns)
    val list = data.txns.filter { it.fundId == f.id }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(f.name, onBack) {
            CircleIconButton(R.drawable.ic_edit, "Edit") { editing = true }
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = Gutter)) {
            item {
                LedgerCard(padding = 20.dp) {
                    Row { TintedIcon(if (isGoal) R.drawable.ic_goal else R.drawable.ic_fund, Color(f.color), 36.dp) }
                    Spacer(Modifier.height(12.dp))
                    Text(if (isGoal) "Saved so far" else "Spent so far", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BigMoney(value, MaterialTheme.colorScheme.onSurface)
                    f.targetMinor?.let { target ->
                        Spacer(Modifier.height(12.dp))
                        Bar(if (target > 0) value.toFloat() / target else 0f, color = Color(f.color))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (isGoal) "${Format.money((target - value).coerceAtLeast(0))} to go of ${Format.money(target)}"
                            else if (value <= target) "${Format.money(target - value)} left of ${Format.money(target)}"
                            else "${Format.money(value - target)} over the ${Format.money(target)} budget",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isGoal && f.startMinor != 0L) {
                        Text("Includes ${Format.money(f.startMinor)} saved before tracking.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { SectionHeader("Assigned transactions") }
            item {
                if (list.isEmpty()) {
                    LedgerCard { EmptyState("Nothing assigned yet", "Open any transaction and tap Assign to fund.") }
                } else {
                    LedgerCard(padding = 4.dp) {
                        Column(Modifier.padding(horizontal = 12.dp)) {
                            list.forEachIndexed { i, t -> TxnRow(t, data, { onOpenTxn(t) }, i < list.lastIndex) }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch(Dispatchers.IO) { AppDb.get(ctx).funds().update(f.copy(archived = !f.archived)) }
                    }, modifier = Modifier.weight(1f)) { Text(if (f.archived) "Unarchive" else "Archive") }
                    OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (editing) FundDialog(f, f.kind) { editing = false }
    if (confirmDelete) {
        ConfirmDialog("Delete ${f.name}?", "Its transactions stay in the log, just without this fund.", "Delete", { confirmDelete = false }) {
            scope.launch(Dispatchers.IO) {
                val db = AppDb.get(ctx)
                db.txns().clearFund(f.id)
                db.funds().delete(f)
            }
            onBack()
        }
    }
}
