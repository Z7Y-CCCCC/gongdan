import { reactive } from 'vue'

// A field-event page can unmount and reopen while its earlier POST still
// runs. Share that lock for the same persisted reference in this app session.
export const fieldEventInFlight = reactive(new Set<string>())
