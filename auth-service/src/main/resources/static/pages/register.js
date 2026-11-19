(function () {
  var form = document.getElementById('form');
  var result = document.getElementById('result');
  var submit = document.getElementById('submit');
  var fields = ['firstName', 'lastName', 'email', 'phoneNo', 'password'];
  var touched = {};

  // Same rules as RegistrationRequest on the server (the server stays the authority).
  function validate(v) {
    var e = {};
    if (!v.firstName) e.firstName = 'First name is mandatory';
    else if (v.firstName.length > 25) e.firstName = 'First name must be at most 25 characters';
    else if (/[0-9]/.test(v.firstName)) e.firstName = 'First name must not contain digits';
    if (!v.lastName) e.lastName = 'Last name is mandatory';
    else if (v.lastName.length > 25) e.lastName = 'Last name must be at most 25 characters';
    else if (/[0-9]/.test(v.lastName)) e.lastName = 'Last name must not contain digits';
    if (!v.email) e.email = 'Email is mandatory';
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v.email)) e.email = 'Invalid format of email';
    if (!v.phoneNo) e.phoneNo = 'Phone number is mandatory';
    else if (!/^[0-9]+$/.test(v.phoneNo)) e.phoneNo = 'Phone number must only be digits';
    else if (v.phoneNo.length < 10 || v.phoneNo.length > 12) e.phoneNo = 'Phone number must be 10 to 12 digits';
    if (v.password.length < 15 || v.password.length > 64) e.password = 'Password must be 15 to 64 characters';
    return e;
  }

  function values() {
    var v = {};
    fields.forEach(function (f) {
      var raw = form.elements[f].value;
      v[f] = f === 'password' ? raw : raw.trim(); // the password is never trimmed
    });
    return v;
  }

  function render() {
    var errors = validate(values());
    fields.forEach(function (f) {
      var input = form.elements[f];
      var msg = touched[f] ? errors[f] : '';
      input.setAttribute('aria-invalid', msg ? 'true' : 'false');
      input.parentNode.querySelector('.field-error').textContent = msg || '';
    });
    return errors;
  }

  form.addEventListener('input', function (ev) {
    touched[ev.target.name] = true;
    render();
  });

  function show(ok, message) {
    result.className = 'notice ' + (ok ? 'notice-success' : 'notice-error');
    result.textContent = message;
    result.hidden = false;
  }

  form.addEventListener('submit', function (ev) {
    ev.preventDefault();
    fields.forEach(function (f) { touched[f] = true; });
    if (Object.keys(render()).length) return;
    submit.disabled = true;
    result.hidden = true;
    fetch('/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(values())
    }).then(function (res) {
      return res.json().catch(function () { return {}; }).then(function (data) {
        if (res.ok) {
          form.reset();
          touched = {};
          render();
          show(true, 'Registration successful! You can now log in.');
        } else if (res.status === 409) {
          show(false, 'A member with this email already exists.');
        } else if (res.status === 429) {
          show(false, 'Too many attempts. Please try again later.');
        } else if (res.status >= 500) {
          show(false, 'Something went wrong on our side. Please try again later.');
        } else {
          show(false, (data.message || 'Registration failed. Please check your details.').replace(/^\w+:\s*/, ''));
        }
      });
    }).catch(function () {
      show(false, 'Network error. Please try again.');
    }).then(function () {
      submit.disabled = false;
    });
  });
})();
