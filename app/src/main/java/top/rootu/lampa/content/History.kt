package top.rootu.lampa.content

import top.rootu.lampa.App
import top.rootu.lampa.helpers.Prefs.CUB
import top.rootu.lampa.helpers.Prefs.FAV
import top.rootu.lampa.helpers.Prefs.histToRemove
import top.rootu.lampa.helpers.Prefs.syncEnabled
import top.rootu.lampa.models.LampaCard

class History : LampaProviderI() {

    override fun get(): LampaContent {
        return LampaContent(History.get())
    }

    companion object {
        fun get(): List<LampaCard> {
            val lst = mutableListOf<LampaCard>()
            if (App.context.syncEnabled) { // CUB
                App.context.CUB
                    ?.filter { it.type == LampaProvider.HIST }
                    ?.sortedByDescending { it.time }
                    ?.mapNotNull { it.data?.apply { fixCard() } }
                    ?.let { lst.addAll(it) }
            } else { // FAV
                App.context.FAV?.card
                    ?.filter { App.context.FAV?.history?.contains(it.id.toString()) == true }
                    ?.sortedBy { App.context.FAV?.history?.indexOf(it.id) }
                    ?.let { lst.addAll(it) }
            }
            // Exclude pending
            return lst
                .filterNot { App.context.histToRemove.contains(it.id.toString()) }
        }
    }
}