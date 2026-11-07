// Timers for the export page. Chrome slows down timers in a tab that is not in front, down to
// once a minute after a few minutes; a worker's timers keep their time, so the export keeps its
// pace while the user does something else.
self.onmessage = (event) => {
  const { id, ms } = event.data;
  setTimeout(() => self.postMessage(id), ms);
};
