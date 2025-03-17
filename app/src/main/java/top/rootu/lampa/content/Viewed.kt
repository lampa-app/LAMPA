package top.rootu.lampa.content

import top.rootu.lampa.App
import top.rootu.lampa.helpers.Prefs.CUB
import top.rootu.lampa.helpers.Prefs.FAV
import top.rootu.lampa.helpers.Prefs.syncEnabled
import top.rootu.lampa.helpers.Prefs.viewToRemove
import top.rootu.lampa.models.LampaCard

class Viewed : LampaProviderI() {

    override fun get(): LampaContent {
        return LampaContent(Viewed.get())
    }

    companion object {
        fun get(): List<LampaCard> {
            val lst = mutableListOf<LampaCard>()
            if (App.context.syncEnabled) { // CUB
                App.context.CUB
                    ?.filter { it.type == LampaProvider.VIEW }
                    ?.mapNotNull { it.data?.apply { fixCard() } }
                    ?.let { lst.addAll(it) }
            } else { // FAV
                App.context.FAV?.card
                    ?.filter { App.context.FAV?.viewed?.contains(it.id.toString()) == true }
                    ?.sortedBy { App.context.FAV?.viewed?.indexOf(it.id) }
                    ?.let { lst.addAll(it) }
            }
            // Exclude pending
            return lst
                .filterNot { App.context.viewToRemove.contains(it.id.toString()) }
        }
    }
}