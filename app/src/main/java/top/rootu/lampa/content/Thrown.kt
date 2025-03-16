package top.rootu.lampa.content

import top.rootu.lampa.App
import top.rootu.lampa.helpers.Prefs.CUB
import top.rootu.lampa.helpers.Prefs.FAV
import top.rootu.lampa.helpers.Prefs.syncEnabled
import top.rootu.lampa.helpers.Prefs.thrwToRemove
import top.rootu.lampa.models.LampaCard

class Thrown : LampaProviderI() {

    override fun get(): ReleaseID {
        return ReleaseID(Thrown.get())
    }

    companion object {
        fun get(): List<LampaCard> {
            val lst = mutableListOf<LampaCard>()
            // CUB
            if (App.context.syncEnabled) {
                App.context.CUB
                    ?.filter { it.type == LampaProvider.THRW }
                    ?.mapNotNull { it.data?.apply { fixCard() } }
                    ?.let { lst.addAll(it) }
            }
            // FAV
            App.context.FAV?.card
                ?.filter { App.context.FAV?.thrown?.contains(it.id.toString()) == true }
                ?.sortedBy { App.context.FAV?.thrown?.indexOf(it.id) }
                ?.let { lst.addAll(it) }
            // Exclude pending and reverse the final list
            return lst
                .filterNot { App.context.thrwToRemove.contains(it.id.toString()) }
        }
    }
}