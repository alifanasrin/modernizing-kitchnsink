"use client";

import { useState } from "react";
import { validate } from "@/lib/validation";

export default function RegisterForm() {
  const [pending, setPending] = useState(false);
  const [result, setResult] = useState<{ ok: boolean; message: string } | null>(
    null,
  );
  const [values, setValues] = useState({
    firstName: "",
    lastName: "",
    email: "",
    phoneNo: "",
    password: "",
  });
  const [touched, setTouched] = useState({
    firstName: false,
    lastName: false,
    email: false,
    phoneNo: false,
    password: false,
  });

  const errors = validate({
    firstName: values.firstName.trim(),
    lastName: values.lastName.trim(),
    email: values.email.trim(),
    phoneNo: values.phoneNo.trim(),
    password: values.password,
  });
  const hasErrors = Object.keys(errors).length > 0;

  const onChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setValues((v) => ({ ...v, [name]: value }));
    setTouched((t) => ({ ...t, [name]: true }));
  };

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (hasErrors) return;
    setPending(true);
    setResult(null);
    try {
      const res = await fetch("/api/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          firstName: values.firstName.trim(),
    lastName: values.lastName.trim(),
          email: values.email.trim(),
          phoneNo: values.phoneNo.trim(),
          password: values.password,
        }),
      });
      const data = await res.json().catch(() => ({}));
      setResult({
        ok: res.ok,
        message:
          data.message ??
          (res.ok ? "Registration successful!" : "Registration failed."),
      });
      if (res.ok) {
        // Reset the whole form (values AND touched), otherwise the emptied fields show "required" errors.
        setValues({ firstName: "", lastName: "", email: "", phoneNo: "", password: "" });
        setTouched({
          firstName: false,
    lastName: false,
          email: false,
          phoneNo: false,
          password: false,
        });
      }
    } catch {
      setResult({ ok: false, message: "Network error. Please try again." });
    } finally {
      setPending(false);
    }
  };

  // Error shows as soon as the user starts typing, and stays until the value is valid.
  const show = (k: "firstName" | "lastName" | "email" | "phoneNo" | "password") =>
    touched[k] && errors[k];

  return (
    <form onSubmit={onSubmit} noValidate className="auth-form">
      <label className="field">
        First name
        <input
          name="firstName"
          value={values.firstName}
          onChange={onChange}
          aria-invalid={!!show("firstName")}
        />
        {show("firstName") && (
          <small role="alert" className="field-error">
            {errors.firstName}
          </small>
        )}
      </label>
      <label className="field">
        Last name
        <input
          name="lastName"
          value={values.lastName}
          onChange={onChange}
          aria-invalid={!!show("lastName")}
        />
        {show("lastName") && (
          <small role="alert" className="field-error">
            {errors.lastName}
          </small>
        )}
      </label>
      <label className="field">
        Email
        <input
          name="email"
          type="email"
          value={values.email}
          onChange={onChange}
          aria-invalid={!!show("email")}
        />
        {show("email") && (
          <small role="alert" className="field-error">
            {errors.email}
          </small>
        )}
      </label>
      <label className="field">
        Phone number
        <input
          name="phoneNo"
          type="tel"
          inputMode="numeric"
          value={values.phoneNo}
          onChange={onChange}

          aria-invalid={!!show("phoneNo")}
        />
        {show("phoneNo") && (
          <small role="alert" className="field-error">
            {errors.phoneNo}
          </small>
        )}
      </label>
      <label className="field">
        Password
        <input
          name="password"
          type="password"
          autoComplete="new-password"
          value={values.password}
          onChange={onChange}

          aria-invalid={!!show("password")}
        />
        {show("password") && (
          <small role="alert" className="field-error">
            {errors.password}
          </small>
        )}
      </label>
      <button
        type="submit"
        disabled={pending || hasErrors}
        className="btn-primary"
      >
        {pending ? "Registering…" : "Register"}
      </button>
      {result && (
        <p
          role="status"
          className={`notice ${result.ok ? "notice-success" : "notice-error"}`}
        >
          {result.message}
        </p>
      )}
    </form>
  );
}
