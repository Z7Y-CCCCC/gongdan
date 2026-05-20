# Requirements Document

## Introduction

This feature unifies all delete confirmation dialogs across the system into a single, reusable, beautifully styled modal component. Currently, the application uses a mix of native browser `confirm()` dialogs (showing "localhost:5173 显示" with plain text) and an inline custom confirm modal (with red X icon, "删除确认" title, and action buttons). The goal is to replace all these inconsistent patterns with one polished, modern `DeleteConfirmDialog` component that provides a consistent user experience for all destructive operations.

## Glossary

- **Delete_Confirm_Dialog**: The reusable Vue 3 component that displays a modal overlay with a warning icon, title, descriptive message, and cancel/confirm action buttons for confirming destructive operations.
- **Modal_Backdrop**: The semi-transparent overlay layer that covers the page content behind the dialog, preventing interaction with the underlying UI.
- **Caller**: Any Vue component or composable that invokes the Delete_Confirm_Dialog to confirm a destructive action before executing it.
- **Confirmation_Callback**: The async function provided by the Caller that executes the actual delete operation when the user confirms.

## Requirements

### Requirement 1: Reusable Delete Confirmation Component

**User Story:** As a developer, I want a single reusable delete confirmation modal component, so that all delete operations across the application use a consistent, polished UI.

#### Acceptance Criteria

1. THE Delete_Confirm_Dialog SHALL be implemented as a standalone Vue 3 Single File Component (SFC) located at `frontend/src/components/DeleteConfirmDialog.vue`.
2. THE Delete_Confirm_Dialog SHALL accept a `visible` prop (boolean) controlling whether the modal is displayed, rendering a full-screen overlay with a centered dialog when `visible` is `true` and rendering nothing when `visible` is `false`.
3. THE Delete_Confirm_Dialog SHALL accept a `title` prop (string, maximum 50 characters) for the modal heading, defaulting to "删除确认".
4. THE Delete_Confirm_Dialog SHALL accept a `message` prop (string, maximum 200 characters) for the descriptive body text explaining what will be deleted.
5. WHEN the user clicks the confirm button, THE Delete_Confirm_Dialog SHALL emit a `confirm` event.
6. WHEN the user clicks the cancel button, clicks the overlay backdrop, or presses the Escape key, THE Delete_Confirm_Dialog SHALL emit an `update:visible` event with value `false`, supporting v-model binding.
7. WHILE the `loading` prop is `true`, THE Delete_Confirm_Dialog SHALL disable the confirm button, display a loading spinner indicator on the confirm button, and disable all dismiss actions (cancel button, overlay click, and Escape key).
8. THE Delete_Confirm_Dialog SHALL apply `role="dialog"`, `aria-modal="true"`, and `aria-labelledby` referencing the title element for accessibility compliance.
9. THE Delete_Confirm_Dialog SHALL display a confirm button labeled "确定" and a cancel button labeled "取消".

### Requirement 2: Visual Design and Layout

**User Story:** As a user, I want the delete confirmation dialog to look modern and polished, so that I have a clear, professional experience when performing destructive actions.

#### Acceptance Criteria

1. THE Delete_Confirm_Dialog SHALL display a red warning icon (circle with X) at the top-left area of the modal content, with a minimum icon size of 24px × 24px.
2. THE Delete_Confirm_Dialog SHALL display the title text in bold font with a minimum font size of 16px, positioned adjacent to or below the warning icon.
3. THE Delete_Confirm_Dialog SHALL display the message body text in a secondary color (#6d7775 or similar muted tone) below the title, with a minimum font size of 14px.
4. THE Delete_Confirm_Dialog SHALL display a "取消" (cancel) button styled as a ghost/outline button.
5. THE Delete_Confirm_Dialog SHALL display a "确定" (confirm) button styled with a red background (#d74434) and white text, maintaining a minimum contrast ratio of 4.5:1 between text and background.
6. THE Delete_Confirm_Dialog SHALL render the action buttons in a footer area aligned to the right at the bottom of the modal, with the "确定" button positioned after the "取消" button in reading order.
7. THE Delete_Confirm_Dialog SHALL have rounded corners (border-radius of 12px or greater) and a box-shadow with a vertical offset between 2px and 8px and an opacity no greater than 0.15.
8. THE Delete_Confirm_Dialog SHALL have internal padding of minimum 24px on all sides.
9. THE Delete_Confirm_Dialog SHALL have a width between 320px and 480px, centered horizontally within the viewport.

### Requirement 3: Modal Behavior and Accessibility

**User Story:** As a user, I want the delete confirmation dialog to behave like a proper modal, so that I can focus on the confirmation decision without accidental interactions.

#### Acceptance Criteria

1. WHEN the Delete_Confirm_Dialog is visible, THE Modal_Backdrop SHALL cover the entire viewport with a semi-transparent dark overlay (rgba black with opacity between 0.3 and 0.5).
2. WHEN the user clicks the Modal_Backdrop area outside the dialog, THE Delete_Confirm_Dialog SHALL close by emitting `update:visible` with value `false`.
3. WHILE the Delete_Confirm_Dialog is visible, WHEN the user presses the Escape key, THE Delete_Confirm_Dialog SHALL close by emitting `update:visible` with value `false`.
4. WHEN the Delete_Confirm_Dialog opens, THE Delete_Confirm_Dialog SHALL apply a CSS transition animation (fade-in, scale, or combination) lasting between 150ms and 300ms, and SHALL apply a corresponding reverse transition of the same duration when closing.
5. THE Delete_Confirm_Dialog SHALL use `role="dialog"`, `aria-modal="true"`, and `aria-labelledby` referencing the dialog title element for screen reader accessibility.
6. WHILE the `loading` prop is true, THE Delete_Confirm_Dialog SHALL disable the confirm button, the backdrop-click-to-close behavior, and the Escape-key-to-close behavior to prevent double submissions.
7. WHEN the Delete_Confirm_Dialog opens, THE Delete_Confirm_Dialog SHALL move keyboard focus to the cancel button within the dialog.

### Requirement 4: Replace Native confirm() in WorkOrderPage

**User Story:** As a user, I want the work order delete actions to use the new styled modal, so that I no longer see the ugly browser-native confirm dialog.

#### Acceptance Criteria

1. WHEN the user clicks delete on a single work order, THE WorkOrderPage SHALL display the Delete_Confirm_Dialog with a message containing the work order code.
2. WHEN the user clicks batch delete for selected work orders, THE WorkOrderPage SHALL display the Delete_Confirm_Dialog with a message indicating the count of selected work orders.
3. WHEN the user confirms deletion in the Delete_Confirm_Dialog, THE WorkOrderPage SHALL execute the delete API call and update the list.
4. WHEN the user cancels in the Delete_Confirm_Dialog, THE WorkOrderPage SHALL take no delete action and close the dialog.

### Requirement 5: Replace Native confirm() in ProductDefinitionPage

**User Story:** As a user, I want the product delete action to use the new styled modal, so that the experience is consistent with the rest of the application.

#### Acceptance Criteria

1. WHEN the user clicks delete on a product, THE ProductDefinitionPage SHALL display the Delete_Confirm_Dialog with a message containing the product name.
2. WHEN the user confirms deletion in the Delete_Confirm_Dialog, THE ProductDefinitionPage SHALL execute the delete API call and update the product list.
3. WHEN the user cancels in the Delete_Confirm_Dialog, THE ProductDefinitionPage SHALL take no delete action and close the dialog.

### Requirement 6: Replace Native confirm() in CustomWorkOrderFieldsPage

**User Story:** As a user, I want the custom field delete action to use the new styled modal, so that the experience is consistent with the rest of the application.

#### Acceptance Criteria

1. WHEN the user clicks delete on a custom field, THE CustomWorkOrderFieldsPage SHALL display the Delete_Confirm_Dialog with a message containing the field name.
2. WHEN the user confirms deletion in the Delete_Confirm_Dialog, THE CustomWorkOrderFieldsPage SHALL execute the delete API call and update the field list.
3. WHEN the user cancels in the Delete_Confirm_Dialog, THE CustomWorkOrderFieldsPage SHALL take no delete action and close the dialog.

### Requirement 7: Replace Inline Confirm Modal in App.vue

**User Story:** As a developer, I want the existing inline confirmState modal in App.vue to be replaced with the new Delete_Confirm_Dialog component, so that the codebase has a single source of truth for confirmation dialogs.

#### Acceptance Criteria

1. THE App.vue SHALL replace the inline `confirmState` modal template (the `<div v-if="confirmState.show">` block) with an instance of the Delete_Confirm_Dialog component.
2. THE App.vue SHALL continue to support the existing `confirmState` reactive pattern (title, message, action) by binding them to the Delete_Confirm_Dialog props and events.
3. WHEN child components emit a `confirm` event payload (title, message, action), THE App.vue SHALL pass these values to the Delete_Confirm_Dialog and display it.

### Requirement 8: Add Confirmation to Unprotected Delete Operations

**User Story:** As a user, I want all delete operations to require confirmation, so that I am protected from accidental data loss even in base data management pages.

#### Acceptance Criteria

1. WHEN the user clicks delete on a unit in BaseDataPage, THE BaseDataPage SHALL display the Delete_Confirm_Dialog with a message containing the unit name before executing the deletion.
2. WHEN the user clicks delete on a defect item in BaseDataPage, THE BaseDataPage SHALL display the Delete_Confirm_Dialog with a message containing the defect item name before executing the deletion.
3. WHEN the user clicks delete on a process in BaseDataPage, THE BaseDataPage SHALL display the Delete_Confirm_Dialog with a message containing the process name before executing the deletion.
4. WHEN the user cancels any of these confirmations, THE BaseDataPage SHALL take no delete action and close the dialog.

### Requirement 9: Composable for Programmatic Usage

**User Story:** As a developer, I want a composable function that provides a promise-based API for showing the delete confirmation dialog, so that I can use it conveniently in async functions without managing visibility state manually.

#### Acceptance Criteria

1. THE system SHALL provide a `useDeleteConfirm` composable located at `frontend/src/composables/useDeleteConfirm.ts`.
2. THE useDeleteConfirm composable SHALL expose a `confirm(options)` function that accepts an object with a required `message` field (string) and an optional `title` field (string) that defaults to "删除确认" when not provided.
3. WHEN the `confirm` function is called, THE useDeleteConfirm composable SHALL return a Promise that resolves to `true` when the user clicks the confirm button, and resolves to `false` when the user clicks cancel, clicks the backdrop, or presses the Escape key.
4. THE useDeleteConfirm composable SHALL expose reactive state (`visible`, `title`, `message`, `loading`) that can be bound to a Delete_Confirm_Dialog instance in the template, where `loading` is a writable ref that the Caller sets to `true` before an async operation and resets to `false` after completion.
5. IF the `confirm` function is called while a previous dialog is still visible, THEN THE useDeleteConfirm composable SHALL resolve the previous Promise with `false` before displaying the new dialog.
