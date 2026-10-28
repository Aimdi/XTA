# Pixiv profile: the Kanten style

The owner's design canvas "QuaX Home – kompakter Header" sets the current
Pixiv profile header beside the same header in the "Kanten" style, the X
profile's second concept: rounded rectangles with clear edges instead of
pills.

## Before

- The follow button was a tonal pill, beside the names or under them when
  the name needed the room, and Add to group an icon beside it.
- The Premium badge was a filled chip.
- The counts read "139 works 0 following 0 My pixiv friends" in one weight.
- No line separated the header from the tabs.
- The back arrow was grey, the other bar icons in the text colour.

## After

- **The buttons take a row of their own under the names.** Follow fills the
  row's width; it is filled in the accent with an 8 dp corner while the
  reader does not follow, and outlined once they do, saying Unfollow. A tap
  and a long press do what they did. Add to group sits beside it as an
  outlined button with the same corner, its icon and its name. When the name
  does not fit beside Follow, as with large text on a narrow phone, the
  button keeps its icon and its tooltip.
- **Premium is an outlined badge** in the accent, with a 4 dp corner.
- **Counts lead with their number in bold**, the rest in the secondary
  colour: **139** works, **0** following, **0** My pixiv friends. The words
  are the existing strings: the number is found in its label and set bold,
  so every language keeps its own word order. German writes a count in full
  as on the X profile, 1.500 rather than 1500; the helper moves to
  `lib/utils/formatting_locale.dart` so both profiles share it.
- **A hairline** closes the header above the tabs.
- **The bar's back arrow** is drawn in the text colour like the share and
  menu icons.
- Targets stay 48 dp where the canvas drew 44.

The canvas names the second button "My pixiv", reading its icon as a friend
request. XTA's button there is Add to group, and XTA sends no My pixiv
requests, so the button keeps its name and what it does.

Unchanged: the header image, the avatar and its save action, the names, the
bio, the tabs and everything below them. Follow buttons in creator cards,
search and the work detail keep their tonal pill. No new strings.

## Tests

- Follow is a filled, 8 dp-cornered button across the row while not
  following, and outlined once following; Add to group sits beside it with
  its name, and keeps only its icon and tooltip at 320 dp with text ×2.
- Your own profile shows Add to group alone.
- The Premium badge is outlined.
- A count's number is bold and its words are not, wherever the language puts
  the number; German writes 1.500.
- A hairline sits between the header and the tabs.
