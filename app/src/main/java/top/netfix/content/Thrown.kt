package top.netfix.content

import top.netfix.App
import top.netfix.helpers.Prefs.CUB
import top.netfix.helpers.Prefs.FAV
import top.netfix.helpers.Prefs.syncEnabled
import top.netfix.helpers.Prefs.thrwToRemove
import top.netfix.models.LampaCard

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
                    ?.reversed()
                    ?.mapNotNull { it.data?.apply { fixCard() } }
                    ?.let { lst.addAll(it) }
            }
            // FAV
            App.context.FAV?.card
                ?.filter { App.context.FAV?.thrown?.contains(it.id.toString()) == true }
                ?.let { lst.addAll(it) }
            // Exclude pending and reverse the final list
            return lst
                .filterNot { App.context.thrwToRemove.contains(it.id.toString()) }
                .reversed()
        }
    }
}