# Plugin preview channels

Android TV plugins can publish a separate preview channel with the native bridge.
Feature-detect both methods before enabling the setting:

```javascript
var supported = typeof AndroidJS !== 'undefined' &&
    typeof AndroidJS.publishPluginChannel === 'function' &&
    typeof AndroidJS.clearPluginChannel === 'function';
if (supported) {
    AndroidJS.publishPluginChannel(JSON.stringify({
        id: 'example',
        title: 'Example shows',
        items: [{id: '1399', source: 'tmdb', type: 'tv',
                 name: 'Example show', poster_path: '/poster.jpg'}]
    }));
}
```

`publishPluginChannel(json)` and `clearPluginChannel(id)` return `true` when
validation succeeds and the operation is queued. This is **not** confirmation
that the TV provider finished writing. Android API 26 or newer and an available
TV provider are required; unsupported devices return `false`.

The channel id must contain 1–64 ASCII characters from `[a-z0-9._-]`. The native
name is `plugin:<id>`: publishing `book` cannot overwrite built-in bookmarks.
The title must be a nonempty string without control characters. JSON is limited
to 1,048,576 characters; `items` must be an array. Malformed JSON or schema returns
`false`, preserving the previous programs. Nesting is limited to 64 levels to
protect the bridge parser; this does not limit the number of list items.

Each item is an ordinary Lampa card with a nonempty string or numeric `id`, a
nonempty `name` or `title`, and `type` equal to `movie` or `tv`. Cards missing
identity or title are skipped. A nonempty list without any usable cards is
rejected rather than treated as a request to clear. Other malformed card fields
reject the entire request. Unknown extension fields are ignored. No Intent or
JavaScript from the payload is executed; selecting a program opens the existing
Lampa card page through an explicit native Intent.

Card ids are restricted to `[A-Za-z0-9._:-]`, 1–256 characters (numeric TMDB,
`KP_` ids and UUIDs are supported). Optional sources contain `[A-Za-z0-9._-]`,
1–64 characters. These restrictions keep native navigation parameters safe.
Preview metadata accepted from cards: `name`, `title`, `original_name`,
`original_title`, `overview`, `img`, `poster_path`, `backdrop_path`,
`background_image`, `original_language`, `release_year`, `release_date`,
`first_air_date`, `runtime`, `number_of_seasons`, `number_of_episodes` and
`vote_average`. Nested extension data is ignored. Direct image URLs in `img`
and `background_image` must use HTTP(S); relative TMDB `poster_path` and
`backdrop_path` are resolved with the existing Lampa image configuration.

An empty `items` array clears programs. `clearPluginChannel('example')` also
clears programs, retaining the channel id and launcher placement. Clearing an
unknown channel does not create one. Operations execute in acceptance order;
rapid updates and a following clear are not dropped. Programs are replaced
with one TV-provider batch. A provider error is logged without the payload and
does not stop later queued operations.

Plugins own opt-in, profile changes and logout: clear old programs before
publishing the new profile. Publish only successful lists; an API error is not
an empty list. There is no background refresh while the plugin is inactive.
Launchers control channel visibility and ordering independently.
