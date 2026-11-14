import { useEffect, useMemo, useRef, useState } from "react";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080/api/news";
// avatar-service (Homework 12). Each post's picture is stored there under news-<id>.
const AVATAR_URL = import.meta.env.VITE_AVATAR_URL ?? "http://localhost:8090";

const emptyForm = { title: "", content: "" };

const avatarUrl = (newsId) => `${AVATAR_URL}/avatars/news-${newsId}`;

// A post without a picture answers 404; the image then simply hides itself.
function Avatar({ newsId, version }) {
  const [missing, setMissing] = useState(false);
  if (missing) return null;
  return (
    <img
      className="avatar"
      data-testid={`news-avatar-${newsId}`}
      src={version ? `${avatarUrl(newsId)}?v=${version}` : avatarUrl(newsId)}
      alt=""
      onError={() => setMissing(true)}
    />
  );
}

function App() {
  const [news, setNews] = useState([]);
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState(null);
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [avatarService, setAvatarService] = useState({ ok: false, detail: "checking..." });
  const [avatarFile, setAvatarFile] = useState(null);
  // Bumped after an upload so the browser fetches the new picture.
  const [avatarVersions, setAvatarVersions] = useState({});
  const avatarInput = useRef(null);

  const endpoint = useMemo(() => {
    if (statusFilter === "ALL") return API_URL;
    return `${API_URL}?status=${statusFilter}`;
  }, [statusFilter]);

  const fetchNews = async () => {
    setLoading(true);
    setError("");
    try {
      const response = await fetch(endpoint);
      if (!response.ok) throw new Error("Failed to load news");
      const data = await response.json();
      setNews(data);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  // The news works without avatar-service; pictures are only offered when it is healthy.
  const checkAvatarService = async () => {
    try {
      const response = await fetch(`${AVATAR_URL}/health`);
      const body = await response.json();
      setAvatarService(response.ok ? { ok: true } : { ok: false, detail: body.detail });
    } catch {
      setAvatarService({ ok: false, detail: `avatar-service is not reachable at ${AVATAR_URL}` });
    }
  };

  useEffect(() => {
    fetchNews();
  }, [endpoint]);

  useEffect(() => {
    checkAvatarService();
  }, []);

  const refresh = () => {
    fetchNews();
    checkAvatarService();
  };

  const onChange = (event) => {
    setForm((prev) => ({ ...prev, [event.target.name]: event.target.value }));
  };

  const resetForm = () => {
    setForm(emptyForm);
    setEditingId(null);
    setAvatarFile(null);
    if (avatarInput.current) avatarInput.current.value = "";
  };

  const uploadAvatar = async (newsId, file) => {
    const body = new FormData();
    body.append("file", file);
    let response;
    try {
      response = await fetch(avatarUrl(newsId), { method: "POST", body });
    } catch {
      throw new Error(`News saved, but not its picture: avatar-service is not reachable at ${AVATAR_URL}`);
    }
    if (!response.ok) {
      const detail = await response
        .json()
        .then((json) => json.detail)
        .catch(() => `HTTP ${response.status}`);
      throw new Error(`News saved, but not its picture: ${detail}`);
    }
  };

  const onSubmit = async (event) => {
    event.preventDefault();
    setError("");
    const payload = {
      title: form.title.trim(),
      content: form.content.trim()
    };
    if (!payload.title || !payload.content) {
      setError("Title and content are required");
      return;
    }

    const method = editingId ? "PUT" : "POST";
    const url = editingId ? `${API_URL}/${editingId}` : API_URL;

    let saved;
    try {
      const response = await fetch(url, {
        method,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });
      if (!response.ok) throw new Error("Saving failed");
      saved = await response.json();
    } catch (e) {
      setError(e.message);
      return;
    }

    const file = avatarFile;
    resetForm();
    await fetchNews();
    // After fetchNews, which clears the error message, so an upload error stays visible.
    if (file) {
      try {
        await uploadAvatar(saved.id, file);
        setAvatarVersions((prev) => ({ ...prev, [saved.id]: Date.now() }));
      } catch (e) {
        setError(e.message);
      }
    }
  };

  const edit = (item) => {
    setEditingId(item.id);
    setForm({ title: item.title, content: item.content });
  };

  const changeStatus = async (id, action) => {
    setError("");
    try {
      const response = await fetch(`${API_URL}/${id}/${action}`, { method: "PATCH" });
      if (!response.ok) throw new Error("Status update failed");
      await fetchNews();
    } catch (e) {
      setError(e.message);
    }
  };

  const remove = async (id) => {
    setError("");
    try {
      const response = await fetch(`${API_URL}/${id}`, { method: "DELETE" });
      if (!response.ok) throw new Error("Delete failed");
      // Best effort: the post is gone either way, and a 404 only means it had no picture.
      if (avatarService.ok) fetch(avatarUrl(id), { method: "DELETE" }).catch(() => {});
      await fetchNews();
    } catch (e) {
      setError(e.message);
    }
  };

  return (
    <main className="container">
      <h1>Student News</h1>
      <p className="muted">Demo app with no authentication</p>

      <form className="card" data-testid="news-form" onSubmit={onSubmit}>
        <h2>{editingId ? "Edit news" : "Create news"}</h2>
        <input
          name="title"
          data-testid="input-title"
          placeholder="Title"
          value={form.title}
          onChange={onChange}
          maxLength={255}
        />
        <textarea
          name="content"
          data-testid="input-content"
          placeholder="News content"
          value={form.content}
          onChange={onChange}
          rows={5}
        />
        <label htmlFor="avatar">
          Picture (PNG or JPEG, optional{editingId ? ", replaces the current one" : ""})
        </label>
        <input
          id="avatar"
          type="file"
          accept="image/png,image/jpeg"
          data-testid="input-avatar"
          ref={avatarInput}
          disabled={!avatarService.ok}
          onChange={(e) => setAvatarFile(e.target.files[0] ?? null)}
        />
        {!avatarService.ok && (
          <p className="muted" data-testid="avatar-service-status">
            Pictures are unavailable: {avatarService.detail}
          </p>
        )}
        <div className="actions">
          <button type="submit" data-testid="btn-submit">
            {editingId ? "Save changes" : "Create draft"}
          </button>
          {editingId && (
            <button type="button" className="secondary" data-testid="btn-cancel-edit" onClick={resetForm}>
              Cancel edit
            </button>
          )}
        </div>
      </form>

      <section className="toolbar">
        <label htmlFor="status">Filter by status</label>
        <select
          id="status"
          data-testid="select-status"
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
        >
          <option value="ALL">All</option>
          <option value="DRAFT">Draft</option>
          <option value="PUBLISHED">Published</option>
          <option value="ARCHIVED">Archived</option>
        </select>
        <button type="button" className="secondary" data-testid="btn-refresh" onClick={refresh}>
          Refresh
        </button>
      </section>

      {error && <p className="error" data-testid="error-message">{error}</p>}
      {loading && <p className="muted" data-testid="loading-indicator">Loading...</p>}

      <section className="list" data-testid="news-list">
        {news.map((item) => (
          <article className="card" key={item.id} data-testid={`news-card-${item.id}`}>
            <header className="newsHeader">
              <div className="newsTitle">
                {avatarService.ok && (
                  <Avatar
                    key={avatarVersions[item.id] ?? 0}
                    newsId={item.id}
                    version={avatarVersions[item.id]}
                  />
                )}
                <h3>{item.title}</h3>
              </div>
              <span className={`status ${item.status.toLowerCase()}`} data-testid={`news-status-${item.id}`}>
                {item.status}
              </span>
            </header>
            <p data-testid={`news-content-text-${item.id}`}>{item.content}</p>
            <small className="muted">Updated: {new Date(item.updatedAt).toLocaleString()}</small>
            <div className="actions">
              <button type="button" data-testid={`btn-edit-${item.id}`} onClick={() => edit(item)}>
                Edit
              </button>
              {item.status !== "PUBLISHED" && (
                <button type="button" data-testid={`btn-publish-${item.id}`} onClick={() => changeStatus(item.id, "publish")}>
                  Publish
                </button>
              )}
              {item.status !== "ARCHIVED" && (
                <button type="button" data-testid={`btn-archive-${item.id}`} onClick={() => changeStatus(item.id, "archive")}>
                  Archive
                </button>
              )}
              <button type="button" className="danger" data-testid={`btn-delete-${item.id}`} onClick={() => remove(item.id)}>
                Delete
              </button>
            </div>
          </article>
        ))}
      </section>
    </main>
  );
}

export default App;
