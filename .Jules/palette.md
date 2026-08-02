## 2024-10-27 - Redundant contentDescriptions
**Learning:** Adding `contentDescription` to icons that are placed immediately adjacent to text labels creates a redundant and annoying experience for screen reader users, as the information is read twice.
**Action:** When an icon is purely decorative or its meaning is fully conveyed by adjacent text within the same interactive element (like a Button), set its `contentDescription` to `null`.

## 2024-10-27 - Contextual contentDescriptions in Lists
**Learning:** Generic `contentDescription`s like "Delete" in a list of items are ambiguous for screen reader users, as they don't know *which* item they are deleting without exploring surrounding context.
**Action:** Make `contentDescription`s in list items contextual by including identifying information from the item, e.g., `"Delete ${savedPass.name}"`.

## 2024-10-27 - Dynamic contentDescriptions for Toggle States
**Learning:** A static `contentDescription` on a toggle button (like password visibility) doesn't inform the user of the *current state* or what action the button will perform if clicked.
**Action:** Update the `contentDescription` dynamically based on the state, e.g., `if (passwordVisible) "Hide password" else "Show password"`.
