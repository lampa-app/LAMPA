package top.rootu.lampa.content

import top.rootu.lampa.App
import top.rootu.lampa.helpers.Prefs.CUB
import top.rootu.lampa.helpers.Prefs.FAV
import top.rootu.lampa.helpers.Prefs.contToRemove
import top.rootu.lampa.helpers.Prefs.syncEnabled
import top.rootu.lampa.models.LampaCard

class Continued : LampaProviderI() {

    override fun get(): LampaContent {
        return LampaContent(Continued.get())
    }

    companion object {
        fun get(): List<LampaCard> {
            val lst = mutableListOf<LampaCard>()
            if (App.context.syncEnabled) { // CUB
                App.context.CUB
                    ?.filter { it.type == LampaProvider.CONT }
                    ?.mapNotNull { it.data?.apply { fixCard() } }
                    ?.let { lst.addAll(it) }
            } else { // FAV
                App.context.FAV?.card
                    ?.filter { App.context.FAV?.continued?.contains(it.id.toString()) == true }
                    ?.sortedBy { App.context.FAV?.continued?.indexOf(it.id) }
                    ?.let { lst.addAll(it) }
            }
            // Exclude pending
            return lst
                .filterNot { App.context.contToRemove.contains(it.id.toString()) }
        }
    }
}