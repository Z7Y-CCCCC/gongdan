# Implementation Plan: Delete Confirmation Dialog

## Overview

This plan implements a unified `DeleteConfirmDialog` Vue 3 component and `useDeleteConfirm` composable, then integrates them across all pages that currently use native `confirm()`, inline modals, or no confirmation at all. The implementation uses TypeScript, Vue 3 Composition API, Vitest for unit tests, and fast-check for property-based tests.

## Tasks

- [x] 1. Set up testing infrastructure and core component
  - [x] 1.1 Add testing dev dependencies (vitest, @vue/test-utils, fast-check, jsdom) and configure vitest in the frontend project
    - Add `vitest`, `@vue/test-utils`, `fast-check`, and `jsdom` to `devDependencies` in `frontend/package.json`
    - Create or update `vitest.config.ts` with jsdom environment and path aliases
    - _Requirements: Design Testing Strategy_

  - [x] 1.2 Create the DeleteConfirmDialog.vue component with props, events, template structure, transitions, and accessibility attributes
    - Implement at `frontend/src/components/DeleteConfirmDialog.vue`
    - Props: `visible` (boolean), `title` (string, default "删除确认"), `message` (string), `loading` (boolean)
    - Events: `update:visible`, `confirm`
    - Template: Teleport to body, Transition with fade/scale, backdrop overlay, dialog section with role="dialog", aria-modal, aria-labelledby
    - Red circle-X warning icon, title heading, message paragraph, cancel ("取消") ghost button, confirm ("确定") danger button
    - Focus management: move focus to cancel button on open via nextTick
    - Escape key listener when visible
    - Loading state: disable all dismiss actions and confirm button, show spinner
    - CSS: rounded corners (12px+), box-shadow, padding 24px+, width 320-480px centered, backdrop rgba black 0.3-0.5
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7_

  - [x] 1.3 Create the useDeleteConfirm.ts composable with promise-based API and reactive state
    - Implement at `frontend/src/composables/useDeleteConfirm.ts`
    - Expose: `visible`, `title`, `message`, `loading` (reactive refs), `confirm(options)`, `handleConfirm()`, `handleCancel()`
    - `confirm(options)` returns Promise<boolean>, resolves true on handleConfirm, false on handleCancel
    - If called while previous dialog visible, resolve previous promise with false first
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5_

- [x] 2. Unit and property tests for core component and composable
  - [x] 2.1 Write property test: Visibility toggle correctness (Property 1)
    - **Property 1: Visibility toggle correctness**
    - For any boolean value of `visible`, verify the component renders modal content when true and renders nothing when false
    - Use fast-check `fc.boolean()` arbitrary, minimum 100 iterations
    - **Validates: Requirements 1.2**

  - [x] 2.2 Write property test: Props rendering round-trip (Property 2)
    - **Property 2: Props rendering round-trip**
    - For any valid title (1-50 chars) and message (1-200 chars), verify rendered output contains exact text
    - Use fast-check `fc.string()` with length constraints
    - **Validates: Requirements 1.3, 1.4**

  - [x] 2.3 Write property test: Composable confirm state setup (Property 3)
    - **Property 3: Composable confirm state setup**
    - For any valid DeleteConfirmOptions, verify `confirm()` sets state correctly and promise resolves appropriately
    - Use fast-check to generate random options objects
    - **Validates: Requirements 9.2, 9.3**

  - [x] 2.4 Write property test: Concurrent invocation safety (Property 4)
    - **Property 4: Concurrent invocation safety**
    - For any sequence of two confirm() calls, verify the first promise resolves false before second dialog state is applied
    - Use fast-check to generate sequences of confirm calls
    - **Validates: Requirements 9.5**

  - [x] 2.5 Write unit tests for DeleteConfirmDialog component
    - Test rendering when visible is false (empty DOM)
    - Test rendering when visible is true (correct structure, role, aria attributes)
    - Test default title "删除确认"
    - Test custom title and message display
    - Test confirm button emits `confirm` event
    - Test cancel button emits `update:visible` with false
    - Test backdrop click emits `update:visible` with false
    - Test Escape key emits `update:visible` with false
    - Test loading state disables all interactions
    - Test focus moves to cancel button on open
    - _Requirements: 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 3.5, 3.6, 3.7_

  - [x] 2.6 Write unit tests for useDeleteConfirm composable
    - Test confirm() sets visible, title, message correctly
    - Test promise resolves true on handleConfirm
    - Test promise resolves false on handleCancel
    - Test concurrent calls resolve previous promise with false
    - Test default title when not provided
    - _Requirements: 9.2, 9.3, 9.4, 9.5_

- [x] 3. Checkpoint - Ensure core component and tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Integrate into pages using native confirm()
  - [x] 4.1 Replace window.confirm() in WorkOrderPage.vue with useDeleteConfirm composable
    - Import useDeleteConfirm and DeleteConfirmDialog
    - Replace single delete confirm with composable `confirm({ message })` containing work order code
    - Replace batch delete confirm with composable `confirm({ message })` containing selected count
    - Add DeleteConfirmDialog to template with v-model bindings
    - Handle loading state during API calls
    - _Requirements: 4.1, 4.2, 4.3, 4.4_

  - [x] 4.2 Replace window.confirm() in ProductDefinitionPage.vue with useDeleteConfirm composable
    - Import useDeleteConfirm and DeleteConfirmDialog
    - Replace delete confirm with composable `confirm({ message })` containing product name
    - Add DeleteConfirmDialog to template with v-model bindings
    - Handle loading state during API calls
    - _Requirements: 5.1, 5.2, 5.3_

  - [x] 4.3 Replace window.confirm() in CustomWorkOrderFieldsPage.vue with useDeleteConfirm composable
    - Import useDeleteConfirm and DeleteConfirmDialog
    - Replace delete confirm with composable `confirm({ message })` containing field name
    - Add DeleteConfirmDialog to template with v-model bindings
    - Handle loading state during API calls
    - _Requirements: 6.1, 6.2, 6.3_

- [x] 5. Integrate into App.vue and BaseDataPage
  - [x] 5.1 Replace inline confirmState modal in App.vue with DeleteConfirmDialog component
    - Remove the existing `<div v-if="confirmState.show">` block and its associated styles
    - Import and add DeleteConfirmDialog component
    - Bind existing `confirmState` reactive object (title, message, show) to DeleteConfirmDialog props
    - Wire `confirm` event to execute `confirmState.action()` and `update:visible` to close
    - Ensure child component `@confirm` event pattern still works
    - _Requirements: 7.1, 7.2, 7.3_

  - [x] 5.2 Add delete confirmation to BaseDataPage.vue for unit, defect item, and process deletions
    - Import useDeleteConfirm and DeleteConfirmDialog
    - Add confirmation before unit deletion with unit name in message
    - Add confirmation before defect item deletion with defect item name in message
    - Add confirmation before process deletion with process name in message
    - Add DeleteConfirmDialog to template with v-model bindings
    - Handle loading state during API calls
    - _Requirements: 8.1, 8.2, 8.3, 8.4_

- [x] 6. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- The implementation language is TypeScript with Vue 3 Composition API
- Testing uses Vitest + @vue/test-utils + fast-check + jsdom

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "1.3"] },
    { "id": 2, "tasks": ["2.1", "2.2", "2.5"] },
    { "id": 3, "tasks": ["2.3", "2.4", "2.6"] },
    { "id": 4, "tasks": ["4.1", "4.2", "4.3"] },
    { "id": 5, "tasks": ["5.1", "5.2"] }
  ]
}
```
