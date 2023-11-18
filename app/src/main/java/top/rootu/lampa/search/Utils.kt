package top.rootu.lampa.search

import top.rootu.lampa.tmdb.models.entity.Entity
import java.util.*
import kotlin.math.min

object Utils {
    fun getDistance(ent: Entity, query: String): Int {
        var lvt = 100000
        var lvo = 100000
        ent.title?.let {
            lvt = levenstain(it.lowercase(Locale.getDefault()), query.lowercase(Locale.getDefault()))
        }
        ent.original_title?.let {
            lvo = levenstain(it.lowercase(Locale.getDefault()), query.lowercase(Locale.getDefault()))
        }
        return min(lvt, lvo)
    }

    fun levenstain(str1: String, str2: String): Int {
        val Di_1 = IntArray(str2.length + 1)
        val Di = IntArray(str2.length + 1)
        for (j in 0..str2.length) {
            Di[j] = j // (i == 0)
        }
        for (i in 1..str1.length) {
            System.arraycopy(Di, 0, Di_1, 0, Di_1.size)
            Di[0] = i // (j == 0)
            for (j in 1..str2.length) {
                val cost = if (str1[i - 1] != str2[j - 1]) 1 else 0
                Di[j] = min(
                    Di_1[j] + 1,
                    Di[j - 1] + 1,
                    Di_1[j - 1] + cost
                )
            }
        }
        return Di[Di.size - 1]
    }

    private fun min(n1: Int, n2: Int, n3: Int): Int {
        return min(min(n1, n2), n3)
    }
}